package ac.workflow.aspect;

import ac.workflow.aspect.annotation.TrackFieldChanges;
import ac.workflow.aspect.dto.AggregateChangeMetadata;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.custom.OptimizedAggregateUpdateRepository;
import ac.workflow.service.monitoring.AggregateChangeDetectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Aspect for monitoring aggregate-level field changes.
 *
 * This aspect specifically handles aggregate root entities and provides
 * specialized monitoring for complex aggregates with child entities.
 * It works in conjunction with the general field change monitoring aspect
 * but provides aggregate-specific functionality.
 *
 * Key features:
 * - Aggregate root detection and handling
 * - Child entity change tracking
 * - Optimized aggregate update operations
 * - Comprehensive error handling and logging
 *
 * @author Workflow Team
 * @version 1.0
 */
@Aspect
@Component
@RequiredArgsConstructor
@Log4j2
@Order(90) // Execute before general field change monitoring
public class AggregateFieldChangeMonitoringAspect {

    private final AggregateChangeDetectorService aggregateChangeDetectorService;
    private final OptimizedAggregateUpdateRepository optimizedAggregateUpdateRepository;

    /**
     * Monitors aggregate changes for methods that work with Workflow entities.
     *
     * This advice specifically handles aggregate root entities and provides
     * specialized change detection for complex domain aggregates.
     *
     * @param joinPoint the method execution join point
     * @param trackFieldChanges the annotation containing configuration
     * @param aggregate the workflow aggregate being processed
     * @return the result of the original method execution
     * @throws Throwable if the original method throws an exception
     */
    @Around("@annotation(trackFieldChanges) && args(aggregate,..)")
    public Object monitorAggregateChanges(ProceedingJoinPoint joinPoint,
                                          TrackFieldChanges trackFieldChanges,
                                          Workflow aggregate) throws Throwable {

        // Only process if this is configured as an aggregate root
        if (!trackFieldChanges.isAggregateRoot()) {
            log.debug("Method not configured for aggregate root monitoring, skipping");
            return joinPoint.proceed();
        }

        // Validate the aggregate
        if (aggregate == null) {
            log.debug("Aggregate argument is null, skipping aggregate change monitoring");
            return joinPoint.proceed();
        }

        String aggregateId = aggregate.getExternalWorkflowId();
        if (aggregateId == null) {
            log.warn("Aggregate ID is null, proceeding without monitoring for method: {}",
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        Instant startTime = Instant.now();

        log.debug("Starting aggregate change monitoring for: {} in method: {}",
                aggregateId, joinPoint.getSignature().getName());

        // Capture aggregate snapshot before method execution
        try {
            aggregateChangeDetectorService.captureAggregateSnapshot(aggregate);
        } catch (Exception e) {
            log.error("Failed to capture aggregate snapshot for {}, proceeding without monitoring",
                    aggregateId, e);
            return joinPoint.proceed();
        }

        try {
            // Execute the original method
            Object result = joinPoint.proceed();

            // Check if we've exceeded the timeout
            Duration elapsed = Duration.between(startTime, Instant.now());
            if (elapsed.toMillis() > trackFieldChanges.timeoutMs()) {
                log.warn("Aggregate method execution took {}ms, exceeding timeout of {}ms, skipping change detection",
                        elapsed.toMillis(), trackFieldChanges.timeoutMs());
                return result;
            }

            // Detect aggregate changes after method execution
            AggregateChangeMetadata changeMetadata = null;
            try {
                changeMetadata = aggregateChangeDetectorService.detectAggregateChanges(
                        aggregate,
                        trackFieldChanges.excludeFields(),
                        trackFieldChanges.maxDepth());
            } catch (Exception e) {
                log.error("Failed to detect aggregate changes for {}", aggregateId, e);
                return result;
            }

            // Perform optimized aggregate update if changes detected
            if (changeMetadata != null && changeMetadata.hasAnyChanges()) {
                try {
                    optimizedAggregateUpdateRepository.updateAggregateSelectively(changeMetadata);

                    log.info("Optimized aggregate update performed for: {}, summary: {}, duration: {}ms",
                            aggregateId,
                            changeMetadata.getChangeSummary(),
                            elapsed.toMillis());

                    // Log detailed change information if debug is enabled
                    if (log.isDebugEnabled()) {
                        logDetailedChanges(changeMetadata);
                    }
                } catch (Exception e) {
                    log.error("Failed to perform optimized aggregate update for {}", aggregateId, e);
                    // Don't throw - original method succeeded
                }
            } else {
                log.debug("No aggregate changes detected for: {}, skipping database update", aggregateId);
            }

            return result;

        } finally {
            // Clean up aggregate snapshot - always execute this
            try {
                aggregateChangeDetectorService.clearAggregateSnapshot(aggregate);
                log.debug("Cleared aggregate snapshot for: {}", aggregateId);
            } catch (Exception e) {
                log.warn("Failed to clear aggregate snapshot for {}", aggregateId, e);
            }
        }
    }

    /**
     * Logs detailed change information for debugging purposes.
     *
     * This method provides comprehensive logging of all changes detected
     * in the aggregate, including root changes and child entity changes.
     *
     * @param changeMetadata the aggregate change metadata to log
     */
    private void logDetailedChanges(AggregateChangeMetadata changeMetadata) {
        if (changeMetadata == null) {
            return;
        }

        log.debug("Detailed changes for aggregate: {}", changeMetadata.getAggregateId());

        // Log root changes
        if (changeMetadata.hasRootChanges()) {
            log.debug("Root field changes: {}", changeMetadata.getModifiedRootFields());

            changeMetadata.getModifiedRootFields().forEach(field -> {
                Object oldValue = changeMetadata.getOldRootValues().get(field);
                Object newValue = changeMetadata.getNewRootValues().get(field);
                log.debug("  Field '{}': {} -> {}", field, oldValue, newValue);
            });
        }

        // Log child changes
        if (changeMetadata.hasChildChanges()) {
            log.debug("Child entity changes:");

            changeMetadata.getAddedChildren().forEach(child ->
                    log.debug("  Added: {}", child.getChangeSummary()));

            changeMetadata.getRemovedChildren().forEach(child ->
                    log.debug("  Removed: {}", child.getChangeSummary()));

            changeMetadata.getModifiedChildren().forEach(child ->
                    log.debug("  Modified: {}", child.getChangeSummary()));
        }
    }
}