package ac.workflow.aspect;

import ac.workflow.aspect.annotation.TrackFieldChanges;
import ac.workflow.aspect.dto.FieldChangeMetadata;
import ac.workflow.repository.custom.OptimizedAggregateUpdateRepository;
import ac.workflow.service.monitoring.FieldChangeDetectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

/**
 * Aspect for monitoring field changes in entities.
 * 
 * This aspect intercepts method calls annotated with @TrackFieldChanges
 * and performs the following operations:
 * 1. Captures a snapshot of the entity before method execution
 * 2. Executes the original method
 * 3. Detects changes by comparing before and after states
 * 4. Performs optimized database updates if changes are detected
 * 5. Cleans up snapshots regardless of success or failure
 * 
 * The aspect provides robust error handling and ensures that original
 * method execution is never compromised by monitoring failures.
 * 
 * @author Workflow Team
 * @version 1.0
 */
@Aspect
@Component
@RequiredArgsConstructor
@Log4j2
@Order(100) // Execute after transaction aspects
public class FieldChangeMonitoringAspect {
    
    private final FieldChangeDetectorService fieldChangeDetectorService;
    private final OptimizedAggregateUpdateRepository optimizedUpdateRepository;
    
    /**
     * Monitors field changes for methods annotated with @TrackFieldChanges.
     * 
     * This advice wraps the annotated method execution and performs change
     * detection with comprehensive error handling.
     * 
     * @param joinPoint the method execution join point
     * @param trackFieldChanges the annotation containing configuration
     * @return the result of the original method execution
     * @throws Throwable if the original method throws an exception
     */
    @Around("@annotation(trackFieldChanges)")
    public Object monitorFieldChanges(ProceedingJoinPoint joinPoint, 
                                    TrackFieldChanges trackFieldChanges) throws Throwable {
        
        // Get method arguments
        Object[] args = joinPoint.getArgs();
        if (args.length == 0) {
            log.debug("No arguments found for method: {}, skipping change monitoring", 
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }
        
        // Get the primary entity (first argument)
        Object entity = args[0];
        if (entity == null) {
            log.debug("Entity argument is null for method: {}, skipping change monitoring", 
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }
        
        // Generate a unique key for this entity
        String entityKey = generateEntityKey(entity);
        Instant startTime = Instant.now();
        
        log.debug("Starting field change monitoring for entity: {} in method: {}", 
                entityKey, joinPoint.getSignature().getName());
        
        // Capture snapshot before method execution
        try {
            fieldChangeDetectorService.captureSnapshot(entity, entityKey);
        } catch (Exception e) {
            log.error("Failed to capture snapshot for entity: {}, proceeding without monitoring", 
                    entityKey, e);
            return joinPoint.proceed();
        }
        
        try {
            // Execute the original method
            Object result = joinPoint.proceed();
            
            // Check if we've exceeded the timeout
            Duration elapsed = Duration.between(startTime, Instant.now());
            if (elapsed.toMillis() > trackFieldChanges.timeoutMs()) {
                log.warn("Method execution took {}ms, exceeding timeout of {}ms, skipping change detection", 
                        elapsed.toMillis(), trackFieldChanges.timeoutMs());
                return result;
            }
            
            // Detect changes after method execution
            FieldChangeMetadata changeMetadata = null;
            try {
                changeMetadata = fieldChangeDetectorService.detectChanges(
                        entity, 
                        entityKey, 
                        trackFieldChanges.isAggregateRoot(), 
                        trackFieldChanges.deepComparison(),
                        trackFieldChanges.excludeFields(),
                        trackFieldChanges.maxDepth());
            } catch (Exception e) {
                log.error("Failed to detect changes for entity: {}", entityKey, e);
                return result;
            }
            
            // Perform optimized update if changes detected
            if (changeMetadata != null && changeMetadata.hasChanges()) {
                try {
                    optimizedUpdateRepository.updateAggregateSelectively(changeMetadata);
                    
                    log.info("Optimized update performed for entity: {}, changes: {}, duration: {}ms", 
                            entityKey, 
                            changeMetadata.getChangeSummary(),
                            elapsed.toMillis());
                } catch (Exception e) {
                    log.error("Failed to perform optimized update for entity: {}", entityKey, e);
                    // Don't throw - original method succeeded
                }
            } else {
                log.debug("No changes detected for entity: {}, skipping database update", entityKey);
            }
            
            return result;
            
        } finally {
            // Clean up snapshot - always execute this
            try {
                fieldChangeDetectorService.clearSnapshot(entityKey);
                log.debug("Cleared snapshot for entity: {}", entityKey);
            } catch (Exception e) {
                log.warn("Failed to clear snapshot for entity: {}", entityKey, e);
            }
        }
    }
    
    /**
     * Monitors field changes for classes annotated with @TrackFieldChanges.
     * 
     * This advice handles class-level annotations by delegating to the
     * method-level monitoring logic.
     * 
     * @param joinPoint the method execution join point
     * @param trackFieldChanges the annotation containing configuration
     * @return the result of the original method execution
     * @throws Throwable if the original method throws an exception
     */
    @Around("@within(trackFieldChanges)")
    public Object monitorClassFieldChanges(ProceedingJoinPoint joinPoint, 
                                         TrackFieldChanges trackFieldChanges) throws Throwable {
        return monitorFieldChanges(joinPoint, trackFieldChanges);
    }
    
    /**
     * Generates a unique key for entity identification.
     * 
     * The key is composed of the entity class name and its identifier.
     * If no identifier is found, the object's hash code is used as fallback.
     * 
     * @param entity the entity object
     * @return unique key string for the entity
     */
    private String generateEntityKey(Object entity) {
        if (entity == null) {
            return "null";
        }
        
        String className = entity.getClass().getSimpleName();
        String identifier = extractEntityId(entity);
        
        return className + ":" + identifier;
    }
    
    /**
     * Extracts the entity identifier using reflection.
     * 
     * This method attempts to find and access common identifier fields
     * such as "id", "uuid", "key", etc. If none are found, it falls back
     * to the object's hash code.
     * 
     * @param entity the entity object
     * @return string representation of the entity identifier
     */
    private String extractEntityId(Object entity) {
        if (entity == null) {
            return "null";
        }
        
        // Common identifier field names to check
        String[] idFieldNames = {"id", "uuid", "key", "identifier", "entityId"};
        
        for (String fieldName : idFieldNames) {
            try {
                Field idField = findField(entity.getClass(), fieldName);
                if (idField != null) {
                    idField.setAccessible(true);
                    Object id = idField.get(entity);
                    if (id != null) {
                        return id.toString();
                    }
                }
            } catch (Exception e) {
                // Continue to next field name
                log.debug("Failed to access field '{}' on entity: {}", fieldName, entity.getClass().getSimpleName());
            }
        }
        
        // Fallback to hash code if no identifier field found
        String hashCode = String.valueOf(entity.hashCode());
        log.debug("No identifier field found for entity: {}, using hash code: {}", 
                entity.getClass().getSimpleName(), hashCode);
        return hashCode;
    }
    
    /**
     * Finds a field by name in the class hierarchy.
     * 
     * This method searches for a field in the given class and its superclasses
     * to handle inheritance scenarios.
     * 
     * @param clazz the class to search in
     * @param fieldName the name of the field to find
     * @return the Field object if found, null otherwise
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // Move to superclass
                currentClass = currentClass.getSuperclass();
            }
        }
        
        return null;
    }
}
