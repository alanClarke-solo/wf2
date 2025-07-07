
package ac.workflow.service.monitoring;

import ac.workflow.aspect.dto.FieldChangeMetadata;
import ac.workflow.util.JacksonDeepCloner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for detecting field changes in entities.
 * 
 * This service provides comprehensive field change detection capabilities
 * including snapshot management, deep comparison, and change metadata generation.
 * It supports both simple field changes and complex nested object comparisons.
 * 
 * Key features:
 * - Thread-safe snapshot storage
 * - Deep comparison for nested objects
 * - Field exclusion support
 * - Configurable comparison depth
 * - Comprehensive error handling
 * 
 * @author Workflow Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class FieldChangeDetectorService {
    
    private final JacksonDeepCloner deepCloner;
    
    /**
     * Thread-safe storage for entity snapshots.
     * Key: entity key, Value: snapshot of the entity
     */
    private final Map<String, Object> snapshots = new ConcurrentHashMap<>();
    
    /**
     * Captures a snapshot of the entity state before modification.
     * 
     * This method creates a deep copy of the entity and stores it
     * for later comparison. The snapshot is stored with the provided
     * entity key for retrieval during change detection.
     * 
     * @param entity the entity to capture
     * @param entityKey unique key for the entity
     * @throws IllegalArgumentException if entity or entityKey is null
     */
    public void captureSnapshot(Object entity, String entityKey) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        
        if (entityKey == null || entityKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity key cannot be null or empty");
        }
        
        try {
            // Create deep copy of the entity
            Object snapshot = deepCloner.deepClone(entity);
            snapshots.put(entityKey, snapshot);
            
            log.debug("Captured snapshot for entity: {}", entityKey);
        } catch (Exception e) {
            log.error("Failed to capture snapshot for entity: {}", entityKey, e);
            throw new RuntimeException("Failed to capture snapshot", e);
        }
    }
    
    /**
     * Detects changes between the captured snapshot and current entity state.
     * 
     * This method compares the current entity state with the previously
     * captured snapshot and generates detailed change metadata.
     * 
     * @param entity the current entity state
     * @param entityKey unique key for the entity
     * @param isAggregateRoot whether the entity is an aggregate root
     * @param deepComparison whether to perform deep comparison
     * @param excludeFields fields to exclude from comparison
     * @param maxDepth maximum depth for recursive comparison
     * @return detailed change metadata
     */
    public FieldChangeMetadata detectChanges(Object entity, 
                                           String entityKey, 
                                           boolean isAggregateRoot,
                                           boolean deepComparison,
                                           String[] excludeFields,
                                           int maxDepth) {
        
        if (entity == null) {
            log.warn("Entity is null for key: {}, returning empty change metadata", entityKey);
            return createEmptyChangeMetadata(entityKey, "unknown");
        }
        
        // Get the stored snapshot
        Object snapshot = snapshots.get(entityKey);
        if (snapshot == null) {
            log.warn("No snapshot found for entity: {}, cannot detect changes", entityKey);
            return createEmptyChangeMetadata(entityKey, entity.getClass().getSimpleName());
        }
        
        try {
            // Create change metadata
            FieldChangeMetadata changeMetadata = FieldChangeMetadata.builder()
                    .entityId(extractEntityId(entity))
                    .entityType(entity.getClass().getSimpleName())
                    .changeTimestamp(Instant.now())
                    .isAggregateRoot(isAggregateRoot)
                    .build();
            
            // Convert exclude fields to set for efficient lookup
            Set<String> excludeFieldSet = new HashSet<>(Arrays.asList(excludeFields));
            
            // Compare entities and populate change metadata
            if (deepComparison) {
                compareEntitiesDeep(snapshot, entity, changeMetadata, excludeFieldSet, maxDepth, 0);
            } else {
                compareEntitiesShallow(snapshot, entity, changeMetadata, excludeFieldSet);
            }
            
            log.debug("Detected changes for entity: {}, summary: {}", 
                    entityKey, changeMetadata.getChangeSummary());
            
            return changeMetadata;
            
        } catch (Exception e) {
            log.error("Failed to detect changes for entity: {}", entityKey, e);
            return createEmptyChangeMetadata(entityKey, entity.getClass().getSimpleName());
        }
    }
    
    /**
     * Clears the snapshot for the specified entity.
     * 
     * This method removes the stored snapshot from memory to prevent
     * memory leaks and ensure clean state for future operations.
     * 
     * @param entityKey unique key for the entity
     */
    public void clearSnapshot(String entityKey) {
        if (entityKey == null || entityKey.trim().isEmpty()) {
            log.warn("Cannot clear snapshot: entity key is null or empty");
            return;
        }
        
        Object removed = snapshots.remove(entityKey);
        if (removed != null) {
            log.debug("Cleared snapshot for entity: {}", entityKey);
        } else {
            log.debug("No snapshot found to clear for entity: {}", entityKey);
        }
    }
    
    /**
     * Gets the current count of stored snapshots.
     * 
     * @return number of snapshots currently stored
     */
    public int getSnapshotCount() {
        return snapshots.size();
    }
    
    /**
     * Clears all stored snapshots.
     * 
     * This method is useful for cleanup operations or testing scenarios.
     */
    public void clearAllSnapshots() {
        int count = snapshots.size();
        snapshots.clear();
        log.debug("Cleared all snapshots, count: {}", count);
    }
    
    /**
     * Performs shallow comparison between two entities.
     * 
     * This method compares only the direct fields of the entities
     * without recursing into nested objects.
     * 
     * @param original the original entity snapshot
     * @param current the current entity state
     * @param changeMetadata the change metadata to populate
     * @param excludeFields fields to exclude from comparison
     */
    private void compareEntitiesShallow(Object original, 
                                      Object current, 
                                      FieldChangeMetadata changeMetadata,
                                      Set<String> excludeFields) {
        
        Class<?> entityClass = current.getClass();
        Field[] fields = getAllFields(entityClass);
        
        for (Field field : fields) {
            if (excludeFields.contains(field.getName())) {
                continue;
            }
            
            try {
                field.setAccessible(true);
                Object originalValue = field.get(original);
                Object currentValue = field.get(current);
                
                if (!Objects.equals(originalValue, currentValue)) {
                    changeMetadata.addModifiedField(field.getName());
                    changeMetadata.addOldValue(field.getName(), originalValue);
                    changeMetadata.addNewValue(field.getName(), currentValue);
                }
            } catch (Exception e) {
                log.warn("Failed to compare field '{}' in entity: {}", 
                        field.getName(), entityClass.getSimpleName(), e);
            }
        }
    }
    
    /**
     * Performs deep comparison between two entities.
     * 
     * This method recursively compares nested objects and collections
     * up to the specified maximum depth.
     * 
     * @param original the original entity snapshot
     * @param current the current entity state
     * @param changeMetadata the change metadata to populate
     * @param excludeFields fields to exclude from comparison
     * @param maxDepth maximum depth for recursive comparison
     * @param currentDepth current recursion depth
     */
    private void compareEntitiesDeep(Object original, 
                                   Object current, 
                                   FieldChangeMetadata changeMetadata,
                                   Set<String> excludeFields,
                                   int maxDepth,
                                   int currentDepth) {
        
        if (currentDepth >= maxDepth) {
            log.debug("Maximum comparison depth reached: {}", maxDepth);
            return;
        }
        
        Class<?> entityClass = current.getClass();
        Field[] fields = getAllFields(entityClass);
        
        for (Field field : fields) {
            if (excludeFields.contains(field.getName())) {
                continue;
            }
            
            try {
                field.setAccessible(true);
                Object originalValue = field.get(original);
                Object currentValue = field.get(current);
                
                if (!Objects.equals(originalValue, currentValue)) {
                    changeMetadata.addModifiedField(field.getName());
                    changeMetadata.addOldValue(field.getName(), originalValue);
                    changeMetadata.addNewValue(field.getName(), currentValue);
                    
                    // Recursively compare nested objects if they're complex types
                    if (currentValue != null && originalValue != null && 
                        isComplexType(currentValue.getClass())) {
                        
                        String childKey = field.getName();
                        FieldChangeMetadata childChangeMetadata = FieldChangeMetadata.builder()
                                .entityId(extractEntityId(currentValue))
                                .entityType(currentValue.getClass().getSimpleName())
                                .changeTimestamp(Instant.now())
                                .isAggregateRoot(false)
                                .build();
                        
                        compareEntitiesDeep(originalValue, currentValue, childChangeMetadata, 
                                          excludeFields, maxDepth, currentDepth + 1);
                        
                        if (childChangeMetadata.hasChanges()) {
                            changeMetadata.addChildChange(childKey, childChangeMetadata);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to compare field '{}' in entity: {}", 
                        field.getName(), entityClass.getSimpleName(), e);
            }
        }
    }
    
    /**
     * Gets all fields from a class including inherited fields.
     * 
     * @param clazz the class to get fields from
     * @return array of all fields
     */
    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;
        
        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        
        return fields.toArray(new Field[0]);
    }
    
    /**
     * Checks if a class is a complex type that should be compared recursively.
     * 
     * @param clazz the class to check
     * @return true if it's a complex type, false otherwise
     */
    private boolean isComplexType(Class<?> clazz) {
        // Consider primitive types, wrappers, and common Java types as simple
        return !clazz.isPrimitive() &&
                !clazz.getName().startsWith("java.lang") &&
                !clazz.getName().startsWith("java.util") &&
                !clazz.getName().startsWith("java.time") &&
                !clazz.isEnum();
        
        // Everything else is considered complex
    }
    
    /**
     * Extracts the entity ID using common field names.
     * 
     * @param entity the entity to extract ID from
     * @return string representation of the entity ID
     */
    private String extractEntityId(Object entity) {
        if (entity == null) {
            return "null";
        }
        
        String[] idFieldNames = {"id", "uuid", "key", "identifier", "entityId"};
        
        for (String fieldName : idFieldNames) {
            try {
                Field field = findField(entity.getClass(), fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    Object id = field.get(entity);
                    if (id != null) {
                        return id.toString();
                    }
                }
            } catch (Exception e) {
                // Continue to next field
            }
        }
        
        return String.valueOf(entity.hashCode());
    }
    
    /**
     * Finds a field by name in the class hierarchy.
     * 
     * @param clazz the class to search in
     * @param fieldName the field name to find
     * @return the field if found, null otherwise
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        
        return null;
    }
    
    /**
     * Creates empty change metadata for error scenarios.
     * 
     * @param entityKey the entity key
     * @param entityType the entity type
     * @return empty change metadata
     */
    private FieldChangeMetadata createEmptyChangeMetadata(String entityKey, String entityType) {
        return FieldChangeMetadata.builder()
                .entityId(entityKey)
                .entityType(entityType)
                .changeTimestamp(Instant.now())
                .isAggregateRoot(false)
                .build();
    }

    // Add enhanced methods to existing service if needed
    public FieldChangeMetadata detectChangesWithValidation(Object original, Object current, String[] excludeFields) {
        // Enhanced logic here
        return null;
    }

    public boolean hasSignificantChanges(Object original, Object current, String[] significantFields) {
        // Logic to detect only significant changes
        return false;
    }

}
