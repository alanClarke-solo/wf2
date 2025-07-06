package ac.workflow.service.monitoring;

import ac.workflow.aspect.dto.AggregateChangeMetadata;
import ac.workflow.domain.model.Workflow;
import ac.workflow.util.JacksonDeepCloner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for detecting changes in aggregate root entities.
 * 
 * This service specializes in monitoring complex aggregate entities
 * that may contain collections of child entities. It provides
 * comprehensive change detection for both root-level fields and
 * child entity modifications.
 * 
 * Key features:
 * - Aggregate root change detection
 * - Child entity change tracking
 * - Collection comparison support
 * - Thread-safe operation
 * - Memory efficient snapshot management
 * 
 * @author Workflow Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AggregateChangeDetectorService {
    
    private final JacksonDeepCloner deepCloner;
    
    /**
     * Thread-safe storage for aggregate snapshots.
     * Key: aggregate ID, Value: snapshot of the aggregate
     */
    private final Map<String, Workflow> aggregateSnapshots = new ConcurrentHashMap<>();
    
    /**
     * Captures a snapshot of the aggregate state before modification.
     * 
     * This method creates a deep copy of the entire aggregate including
     * all child entities and stores it for later comparison.
     * 
     * @param aggregate the aggregate to capture
     * @throws IllegalArgumentException if aggregate or its ID is null
     */
    public void captureAggregateSnapshot(Workflow aggregate) {
        if (aggregate == null) {
            throw new IllegalArgumentException("Aggregate cannot be null");
        }
        
        String aggregateId = aggregate.getExternalWorkflowId();
        if (aggregateId == null || aggregateId.trim().isEmpty()) {
            throw new IllegalArgumentException("Aggregate ID cannot be null or empty");
        }
        
        try {
            // Create deep copy of the aggregate
            Workflow snapshot = deepCloner.deepClone(aggregate);
            aggregateSnapshots.put(aggregateId, snapshot);
            
            log.debug("Captured aggregate snapshot for: {}", aggregateId);
        } catch (Exception e) {
            log.error("Failed to capture aggregate snapshot for: {}", aggregateId, e);
            throw new RuntimeException("Failed to capture aggregate snapshot", e);
        }
    }
    
    /**
     * Detects changes in the aggregate by comparing current state with snapshot.
     * 
     * This method performs comprehensive change detection including:
     * - Root-level field changes
     * - Child entity additions, removals, and modifications
     * - Collection changes
     * 
     * @param aggregate the current aggregate state
     * @param excludeFields fields to exclude from comparison
     * @param maxDepth maximum depth for recursive comparison
     * @return detailed aggregate change metadata
     */
    public AggregateChangeMetadata detectAggregateChanges(Workflow aggregate,
                                                        String[] excludeFields,
                                                        int maxDepth) {
        
        if (aggregate == null) {
            log.warn("Aggregate is null, returning empty change metadata");
            return createEmptyAggregateChangeMetadata("unknown", "unknown");
        }
        
        String aggregateId = aggregate.getExternalWorkflowId();
        if (aggregateId == null) {
            log.warn("Aggregate ID is null, returning empty change metadata");
            return createEmptyAggregateChangeMetadata("unknown", aggregate.getClass().getSimpleName());
        }
        
        // Get the stored snapshot
        Workflow snapshot = aggregateSnapshots.get(aggregateId);
        if (snapshot == null) {
            log.warn("No snapshot found for aggregate: {}, cannot detect changes", aggregateId);
            return createEmptyAggregateChangeMetadata(aggregateId, aggregate.getClass().getSimpleName());
        }
        
        try {
            // Create aggregate change metadata
            AggregateChangeMetadata changeMetadata = AggregateChangeMetadata.builder()
                    .aggregateId(aggregateId)
                    .aggregateType(aggregate.getClass().getSimpleName())
                    .changeTimestamp(Instant.now())
                    .build();
            
            // Convert exclude fields to set for efficient lookup
            Set<String> excludeFieldSet = new HashSet<>(Arrays.asList(excludeFields));
            
            // Compare root-level fields
            compareRootFields(snapshot, aggregate, changeMetadata, excludeFieldSet);
            
            // Compare child entities
            compareChildEntities(snapshot, aggregate, changeMetadata, excludeFieldSet, maxDepth);
            
            log.debug("Detected aggregate changes for: {}, summary: {}", 
                    aggregateId, changeMetadata.getChangeSummary());
            
            return changeMetadata;
            
        } catch (Exception e) {
            log.error("Failed to detect aggregate changes for: {}", aggregateId, e);
            return createEmptyAggregateChangeMetadata(aggregateId, aggregate.getClass().getSimpleName());
        }
    }
    
    /**
     * Clears the snapshot for the specified aggregate.
     * 
     * @param aggregate the aggregate whose snapshot should be cleared
     */
    public void clearAggregateSnapshot(Workflow aggregate) {
        if (aggregate == null) {
            log.warn("Cannot clear snapshot: aggregate is null");
            return;
        }
        
        String aggregateId = aggregate.getExternalWorkflowId();
        if (aggregateId == null || aggregateId.trim().isEmpty()) {
            log.warn("Cannot clear snapshot: aggregate ID is null or empty");
            return;
        }
        
        Object removed = aggregateSnapshots.remove(aggregateId);
        if (removed != null) {
            log.debug("Cleared aggregate snapshot for: {}", aggregateId);
        } else {
            log.debug("No snapshot found to clear for aggregate: {}", aggregateId);
        }
    }
    
    /**
     * Gets the current count of stored aggregate snapshots.
     * 
     * @return number of aggregate snapshots currently stored
     */
    public int getSnapshotCount() {
        return aggregateSnapshots.size();
    }
    
    /**
     * Clears all stored aggregate snapshots.
     */
    public void clearAllSnapshots() {
        int count = aggregateSnapshots.size();
        aggregateSnapshots.clear();
        log.debug("Cleared all aggregate snapshots, count: {}", count);
    }
    
    /**
     * Compares root-level fields between snapshot and current aggregate.
     * 
     * @param snapshot the original aggregate snapshot
     * @param current the current aggregate state
     * @param changeMetadata the change metadata to populate
     * @param excludeFields fields to exclude from comparison
     */
    private void compareRootFields(Workflow snapshot,
                                 Workflow current,
                                 AggregateChangeMetadata changeMetadata,
                                 Set<String> excludeFields) {
        
        Class<?> aggregateClass = current.getClass();
        Field[] fields = getAllFields(aggregateClass);
        
        for (Field field : fields) {
            if (excludeFields.contains(field.getName()) || isChildCollectionField(field)) {
                continue; // Skip excluded fields and child collections
            }
            
            try {
                field.setAccessible(true);
                Object originalValue = field.get(snapshot);
                Object currentValue = field.get(current);
                
                if (!Objects.equals(originalValue, currentValue)) {
                    changeMetadata.getModifiedRootFields().add(field.getName());
                    changeMetadata.getOldRootValues().put(field.getName(), originalValue);
                    changeMetadata.getNewRootValues().put(field.getName(), currentValue);
                    
                    log.debug("Root field changed: {} in aggregate: {}", 
                            field.getName(), changeMetadata.getAggregateId());
                }
            } catch (Exception e) {
                log.warn("Failed to compare root field '{}' in aggregate: {}", 
                        field.getName(), aggregateClass.getSimpleName(), e);
            }
        }
    }
    
    /**
     * Compares child entities between snapshot and current aggregate.
     * 
     * @param snapshot the original aggregate snapshot
     * @param current the current aggregate state
     * @param changeMetadata the change metadata to populate
     * @param excludeFields fields to exclude from comparison
     * @param maxDepth maximum depth for recursive comparison
     */
    private void compareChildEntities(Workflow snapshot,
                                    Workflow current,
                                    AggregateChangeMetadata changeMetadata,
                                    Set<String> excludeFields,
                                    int maxDepth) {
        
        Class<?> aggregateClass = current.getClass();
        Field[] fields = getAllFields(aggregateClass);
        
        for (Field field : fields) {
            if (excludeFields.contains(field.getName()) || !isChildCollectionField(field)) {
                continue; // Only process child collection fields
            }
            
            try {
                field.setAccessible(true);
                Object originalCollection = field.get(snapshot);
                Object currentCollection = field.get(current);
                
                compareCollections(originalCollection, currentCollection, 
                                 changeMetadata, field.getName());
                
            } catch (Exception e) {
                log.warn("Failed to compare child collection '{}' in aggregate: {}", 
                        field.getName(), aggregateClass.getSimpleName(), e);
            }
        }
    }
    
    /**
     * Compares two collections and identifies added, removed, and modified items.
     * 
     * @param originalCollection the original collection from snapshot
     * @param currentCollection the current collection state
     * @param changeMetadata the change metadata to populate
     * @param fieldName the name of the field containing the collection
     */
    @SuppressWarnings("unchecked")
    private void compareCollections(Object originalCollection,
                                  Object currentCollection,
                                  AggregateChangeMetadata changeMetadata,
                                  String fieldName) {
        
        if (originalCollection == null && currentCollection == null) {
            return; // Both null, no changes
        }
        
        Collection<Object> originalItems = originalCollection instanceof Collection ? 
                (Collection<Object>) originalCollection : Collections.emptyList();
        Collection<Object> currentItems = currentCollection instanceof Collection ? 
                (Collection<Object>) currentCollection : Collections.emptyList();
        
        // Create maps keyed by entity ID for efficient comparison
        Map<String, Object> originalMap = createEntityMap(originalItems);
        Map<String, Object> currentMap = createEntityMap(currentItems);
        
        // Find added items
        for (Map.Entry<String, Object> entry : currentMap.entrySet()) {
            if (!originalMap.containsKey(entry.getKey())) {
                AggregateChangeMetadata.ChildEntityChange childChange = 
                        AggregateChangeMetadata.ChildEntityChange.builder()
                                .childId(entry.getKey())
                                .childType(entry.getValue().getClass().getSimpleName())
                                .childFieldName(fieldName)
                                .childEntity(entry.getValue())
                                .changeType(AggregateChangeMetadata.ChangeType.ADDED)
                                .build();
                
                changeMetadata.addChildChange(childChange);
                log.debug("Added child entity: {} in field: {}", entry.getKey(), fieldName);
            }
        }
        
        // Find removed items
        for (Map.Entry<String, Object> entry : originalMap.entrySet()) {
            if (!currentMap.containsKey(entry.getKey())) {
                AggregateChangeMetadata.ChildEntityChange childChange = 
                        AggregateChangeMetadata.ChildEntityChange.builder()
                                .childId(entry.getKey())
                                .childType(entry.getValue().getClass().getSimpleName())
                                .childFieldName(fieldName)
                                .childEntity(entry.getValue())
                                .changeType(AggregateChangeMetadata.ChangeType.REMOVED)
                                .build();
                
                changeMetadata.addChildChange(childChange);
                log.debug("Removed child entity: {} in field: {}", entry.getKey(), fieldName);
            }
        }
        
        // Find modified items
        for (Map.Entry<String, Object> entry : currentMap.entrySet()) {
            String entityId = entry.getKey();
            Object currentEntity = entry.getValue();
            Object originalEntity = originalMap.get(entityId);
            
            if (originalEntity != null && !Objects.equals(originalEntity, currentEntity)) {
                AggregateChangeMetadata.ChildEntityChange childChange = 
                        createModifiedChildChange(originalEntity, currentEntity, fieldName);
                
                if (childChange != null) {
                    changeMetadata.addChildChange(childChange);
                    log.debug("Modified child entity: {} in field: {}", entityId, fieldName);
                }
            }
        }
    }
    
    /**
     * Creates a map of entities keyed by their ID for efficient comparison.
     * 
     * @param items the collection of items to map
     * @return map of entity ID to entity
     */
    private Map<String, Object> createEntityMap(Collection<Object> items) {
        Map<String, Object> entityMap = new HashMap<>();
        
        for (Object item : items) {
            if (item != null) {
                String entityId = extractEntityId(item);
                entityMap.put(entityId, item);
            }
        }
        
        return entityMap;
    }
    
    /**
     * Creates a ChildEntityChange for a modified child entity.
     * 
     * @param originalEntity the original entity state
     * @param currentEntity the current entity state
     * @param fieldName the field name containing the entity
     * @return ChildEntityChange or null if no changes detected
     */
    private AggregateChangeMetadata.ChildEntityChange createModifiedChildChange(Object originalEntity,
                                                                               Object currentEntity,
                                                                               String fieldName) {
        
        String entityId = extractEntityId(currentEntity);
        
        AggregateChangeMetadata.ChildEntityChange.ChildEntityChangeBuilder builder = 
                AggregateChangeMetadata.ChildEntityChange.builder()
                        .childId(entityId)
                        .childType(currentEntity.getClass().getSimpleName())
                        .childFieldName(fieldName)
                        .childEntity(currentEntity)
                        .changeType(AggregateChangeMetadata.ChangeType.MODIFIED);
        
        // Compare fields to find specific changes
        Class<?> entityClass = currentEntity.getClass();
        Field[] fields = getAllFields(entityClass);
        
        Set<String> modifiedFields = new HashSet<>();
        Map<String, Object> oldValues = new HashMap<>();
        Map<String, Object> newValues = new HashMap<>();
        
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object originalValue = field.get(originalEntity);
                Object currentValue = field.get(currentEntity);
                
                if (!Objects.equals(originalValue, currentValue)) {
                    modifiedFields.add(field.getName());
                    oldValues.put(field.getName(), originalValue);
                    newValues.put(field.getName(), currentValue);
                }
            } catch (Exception e) {
                log.warn("Failed to compare field '{}' in child entity: {}", 
                        field.getName(), entityClass.getSimpleName(), e);
            }
        }
        
        if (!modifiedFields.isEmpty()) {
            builder.modifiedFields(modifiedFields)
                   .oldValues(oldValues)
                   .newValues(newValues);
            
            return builder.build();
        }
        
        return null; // No changes detected
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
     * Checks if a field represents a child collection.
     * 
     * @param field the field to check
     * @return true if it's a child collection field
     */
    private boolean isChildCollectionField(Field field) {
        Class<?> fieldType = field.getType();
        
        // Check if it's a Collection or List type
        return Collection.class.isAssignableFrom(fieldType) || 
               List.class.isAssignableFrom(fieldType) ||
               Set.class.isAssignableFrom(fieldType);
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
     * Creates empty aggregate change metadata for error scenarios.
     * 
     * @param aggregateId the aggregate ID
     * @param aggregateType the aggregate type
     * @return empty aggregate change metadata
     */
    private AggregateChangeMetadata createEmptyAggregateChangeMetadata(String aggregateId, String aggregateType) {
        return AggregateChangeMetadata.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .changeTimestamp(Instant.now())
                .build();
    }
}
