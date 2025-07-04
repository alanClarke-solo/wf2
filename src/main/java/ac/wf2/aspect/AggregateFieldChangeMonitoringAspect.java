package ac.wf2.aspect;

import ac.wf2.aspect.annotation.TrackFieldChanges;
import ac.wf2.aspect.dto.AggregateChangeMetadata;
import ac.wf2.domain.model.WorkflowAggregate;
import ac.wf2.service.monitoring.AggregateChangeDetectorService;
import ac.wf2.repository.custom.OptimizedAggregateUpdateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AggregateFieldChangeMonitoringAspect {
    
    private final AggregateChangeDetectorService aggregateChangeDetectorService;
    private final OptimizedAggregateUpdateRepository optimizedAggregateUpdateRepository;
    
    @Around("@annotation(trackFieldChanges) && args(aggregate,..)")
    public Object monitorAggregateChanges(ProceedingJoinPoint joinPoint, 
                                        TrackFieldChanges trackFieldChanges,
                                        WorkflowAggregate aggregate) throws Throwable {
        
        if (!trackFieldChanges.isAggregateRoot()) {
            return joinPoint.proceed();
        }
        
        // Capture snapshot before method execution
        aggregateChangeDetectorService.captureAggregateSnapshot(aggregate);
        
        try {
            // Execute the original method
            Object result = joinPoint.proceed();
            
            // Detect changes after method execution
            AggregateChangeMetadata changeMetadata = 
                aggregateChangeDetectorService.detectAggregateChanges(aggregate);
            
            // Perform optimized update if changes detected
            if (changeMetadata.hasAnyChanges()) {
                optimizedAggregateUpdateRepository.updateAggregateSelectively(changeMetadata);
                
                log.info("Optimized aggregate update performed for: {}, root changes: {}, child changes: {}", 
                        changeMetadata.getAggregateId(), 
                        changeMetadata.hasRootChanges(),
                        changeMetadata.hasChildChanges());
            } else {
                log.debug("No changes detected for aggregate: {}, skipping database update", 
                        changeMetadata.getAggregateId());
            }
            
            return result;
            
        } finally {
            // Clean up snapshot
            aggregateChangeDetectorService.clearAggregateSnapshot(aggregate);
        }
    }
}
