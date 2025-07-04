package ac.wf2.aspect;

import ac.wf2.aspect.annotation.TrackFieldChanges;
import ac.wf2.aspect.dto.FieldChangeMetadata;
import ac.wf2.repository.custom.OptimizedAggregateUpdateRepository;
import ac.wf2.service.monitoring.FieldChangeDetectorService;
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
public class FieldChangeMonitoringAspect {
    
    private final FieldChangeDetectorService fieldChangeDetectorService;
    private final OptimizedAggregateUpdateRepository optimizedUpdateRepository;
    
    @Around("@annotation(trackFieldChanges)")
    public Object monitorFieldChanges(ProceedingJoinPoint joinPoint, TrackFieldChanges trackFieldChanges) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length == 0) {
            return joinPoint.proceed();
        }
        
        Object entity = args[0];
        String entityKey = generateEntityKey(entity);
        
        // Capture snapshot before method execution
        fieldChangeDetectorService.captureSnapshot(entity, entityKey);
        
        try {
            // Execute the original method
            Object result = joinPoint.proceed();
            
            // Detect changes after method execution
            FieldChangeMetadata changeMetadata = fieldChangeDetectorService.detectChanges(
                    entity, entityKey, trackFieldChanges.isAggregateRoot(), trackFieldChanges.deepComparison());
            
            // Perform optimized update if changes detected
            if (changeMetadata.hasChanges()) {
                optimizedUpdateRepository.updateAggregateSelectively(changeMetadata);
                log.info("Optimized update performed for entity: {}, changes: {}", 
                        entityKey, changeMetadata.getModifiedFields());
            } else {
                log.debug("No changes detected for entity: {}, skipping database update", entityKey);
            }
            
            return result;
            
        } finally {
            // Clean up snapshot
            fieldChangeDetectorService.clearSnapshot(entityKey);
        }
    }
    
    @Around("@within(trackFieldChanges)")
    public Object monitorClassFieldChanges(ProceedingJoinPoint joinPoint, TrackFieldChanges trackFieldChanges) throws Throwable {
        return monitorFieldChanges(joinPoint, trackFieldChanges);
    }
    
    private String generateEntityKey(Object entity) {
        // Generate unique key based on entity type and identifier
        String className = entity.getClass().getSimpleName();
        String identifier = extractEntityId(entity);
        return className + ":" + identifier;
    }
    
    private String extractEntityId(Object entity) {
        try {
            // Try to get id field through reflection
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(entity);
            return id != null ? id.toString() : "unknown";
        } catch (Exception e) {
            // Fallback to hashCode if no id field found
            return String.valueOf(entity.hashCode());
        }
    }
}
