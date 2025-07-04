package ac.wf2.service.monitoring;

import ac.wf2.aspect.dto.FieldChangeMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FieldChangeDetectorService {
    
    private final Map<String, Object> entitySnapshots = new ConcurrentHashMap<>();
    
    public void captureSnapshot(Object entity, String entityKey) {
        try {
            Object snapshot = deepClone(entity);
            entitySnapshots.put(entityKey, snapshot);
            log.debug("Captured snapshot for entity: {}", entityKey);
        } catch (Exception e) {
            log.error("Failed to capture snapshot for entity: {}", entityKey, e);
        }
    }
    
    public FieldChangeMetadata detectChanges(Object currentEntity, String entityKey, 
                                           boolean isAggregateRoot, boolean deepComparison) {
        Object originalEntity = entitySnapshots.get(entityKey);
        if (originalEntity == null) {
            log.warn("No snapshot found for entity: {}", entityKey);
            return FieldChangeMetadata.builder()
                    .entityId(entityKey)
                    .entityType(currentEntity.getClass().getSimpleName())
                    .modifiedFields(Collections.emptySet())
                    .changeTimestamp(Instant.now())
                    .isAggregateRoot(isAggregateRoot)
                    .build();
        }
        
        return compareObjects(originalEntity, currentEntity, entityKey, isAggregateRoot, deepComparison);
    }
    
    private FieldChangeMetadata compareObjects(Object original, Object current, String entityKey, 
                                             boolean isAggregateRoot, boolean deepComparison) {
        Set<String> modifiedFields = new HashSet<>();
        Map<String, Object> oldValues = new HashMap<>();
        Map<String, Object> newValues = new HashMap<>();
        Map<String, FieldChangeMetadata> childChanges = new HashMap<>();
        
        Class<?> clazz = current.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object originalValue = field.get(original);
                Object currentValue = field.get(current);
                
                if (!Objects.equals(originalValue, currentValue)) {
                    String fieldName = field.getName();
                    modifiedFields.add(fieldName);
                    oldValues.put(fieldName, originalValue);
                    newValues.put(fieldName, currentValue);
                    
                    // Handle nested objects for aggregator roots
                    if (isAggregateRoot && deepComparison && isComplexObject(currentValue)) {
                        FieldChangeMetadata childChange = compareObjects(
                                originalValue, currentValue, 
                                entityKey + "." + fieldName, false, true);
                        if (childChange.hasChanges()) {
                            childChanges.put(fieldName, childChange);
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                log.error("Failed to access field: {} in class: {}", field.getName(), clazz.getSimpleName(), e);
            }
        }
        
        return FieldChangeMetadata.builder()
                .entityId(entityKey)
                .entityType(clazz.getSimpleName())
                .modifiedFields(modifiedFields)
                .oldValues(oldValues)
                .newValues(newValues)
                .changeTimestamp(Instant.now())
                .isAggregateRoot(isAggregateRoot)
                .childChanges(childChanges.isEmpty() ? null : childChanges)
                .build();
    }
    
    private boolean isComplexObject(Object obj) {
        if (obj == null) return false;
        Class<?> clazz = obj.getClass();
        return !clazz.isPrimitive() && 
               !clazz.isEnum() && 
               !String.class.equals(clazz) && 
               !Number.class.isAssignableFrom(clazz) && 
               !Boolean.class.equals(clazz) && 
               !Date.class.isAssignableFrom(clazz) && 
               !Instant.class.equals(clazz);
    }
    
    private Object deepClone(Object original) throws Exception {
        // Simple implementation - for production use a proper deep cloning library
        // like Apache Commons Lang or implement Serializable-based cloning
        return original; // Placeholder - implement proper deep cloning
    }
    
    public void clearSnapshot(String entityKey) {
        entitySnapshots.remove(entityKey);
        log.debug("Cleared snapshot for entity: {}", entityKey);
    }
}
