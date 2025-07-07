package ac.workflow.repository.custom;

import ac.workflow.aspect.dto.AggregateChangeMetadata;
import ac.workflow.aspect.dto.FieldChangeMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository for performing optimized database updates based on detected changes.
 * 
 * This repository provides efficient database update operations that only
 * modify the fields that have actually changed, rather than performing
 * full entity updates. This approach significantly reduces database load
 * and improves application performance.
 * 
 * Key features:
 * - Selective field updates
 * - Batch operations for multiple changes
 * - Transaction support
 * - Comprehensive error handling
 * - Performance optimized queries
 * 
 * @author Workflow Team
 * @version 1.0
 */
@Repository
@RequiredArgsConstructor
@Log4j2
public class OptimizedAggregateUpdateRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * Performs selective update of an aggregate based on detected changes.
     * 
     * This method generates and executes optimized SQL update statements
     * that only modify the fields that have actually changed, providing
     * significant performance improvements over full entity updates.
     * 
     * @param aggregateChangeMetadata metadata containing all detected changes
     * @throws RuntimeException if update operations fail
     */
    @Transactional
    public void updateAggregateSelectively(AggregateChangeMetadata aggregateChangeMetadata) {
        if (aggregateChangeMetadata == null) {
            log.warn("Aggregate change metadata is null, skipping update");
            return;
        }
        
        if (!aggregateChangeMetadata.hasAnyChanges()) {
            log.debug("No changes detected in aggregate: {}, skipping update", 
                    aggregateChangeMetadata.getAggregateId());
            return;
        }
        
        String aggregateId = aggregateChangeMetadata.getAggregateId();
        String aggregateType = aggregateChangeMetadata.getAggregateType();
        
        log.debug("Starting selective update for aggregate: {} of type: {}", 
                aggregateId, aggregateType);
        
        try {
            // Update root fields if changed
            if (aggregateChangeMetadata.hasRootChanges()) {
                updateRootFields(aggregateChangeMetadata);
            }
            
            // Process child entity changes
            if (aggregateChangeMetadata.hasChildChanges()) {
                processChildEntityChanges(aggregateChangeMetadata);
            }
            
            // Update the aggregate's last modified timestamp
            updateLastModifiedTimestamp(aggregateId, aggregateType);
            
            log.info("Successfully completed selective update for aggregate: {}", aggregateId);
            
        } catch (Exception e) {
            log.error("Failed to perform selective update for aggregate: {}", aggregateId, e);
            throw new RuntimeException("Selective aggregate update failed", e);
        }
    }
    
    /**
     * Performs selective update of a single entity based on field changes.
     * 
     * This method is used for simple entity updates where only specific
     * fields have changed.
     * 
     * @param fieldChangeMetadata metadata containing field changes
     * @throws RuntimeException if update operations fail
     */
    @Transactional
    public void updateAggregateSelectively(FieldChangeMetadata fieldChangeMetadata) {
        if (fieldChangeMetadata == null) {
            log.warn("Field change metadata is null, skipping update");
            return;
        }
        
        if (!fieldChangeMetadata.hasChanges()) {
            log.debug("No changes detected in entity: {}, skipping update", 
                    fieldChangeMetadata.getEntityId());
            return;
        }
        
        String entityId = fieldChangeMetadata.getEntityId();
        String entityType = fieldChangeMetadata.getEntityType();
        
        log.debug("Starting selective update for entity: {} of type: {}", 
                entityId, entityType);
        
        try {
            // Update modified fields
            if (fieldChangeMetadata.hasModifiedFields()) {
                updateEntityFields(fieldChangeMetadata);
            }
            
            // Process child changes if this is an aggregate root
            if (fieldChangeMetadata.isAggregateRoot() && fieldChangeMetadata.hasChildChanges()) {
                processChildFieldChanges(fieldChangeMetadata);
            }
            
            // Update the entity's last modified timestamp
            updateLastModifiedTimestamp(entityId, entityType);
            
            log.info("Successfully completed selective update for entity: {}", entityId);
            
        } catch (Exception e) {
            log.error("Failed to perform selective update for entity: {}", entityId, e);
            throw new RuntimeException("Selective entity update failed", e);
        }
    }
    
    /**
     * Updates root-level fields of an aggregate.
     * 
     * @param aggregateChangeMetadata the aggregate change metadata
     */
    private void updateRootFields(AggregateChangeMetadata aggregateChangeMetadata) {
        String aggregateId = aggregateChangeMetadata.getAggregateId();
        String aggregateType = aggregateChangeMetadata.getAggregateType();
        Set<String> modifiedFields = aggregateChangeMetadata.getModifiedRootFields();
        Map<String, Object> newValues = aggregateChangeMetadata.getNewRootValues();
        
        if (modifiedFields.isEmpty()) {
            return;
        }
        
        // Build dynamic SQL for updating only changed fields
        String tableName = getTableName(aggregateType);
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        
        List<Object> parameters = new ArrayList<>();
        List<String> setClause = new ArrayList<>();
        
        for (String field : modifiedFields) {
            String columnName = convertToColumnName(field);
            setClause.add(columnName + " = ?");
            parameters.add(newValues.get(field));
        }
        
        sql.append(String.join(", ", setClause));
        sql.append(" WHERE id = ?");
        parameters.add(aggregateId);
        
        // Execute the update
        int rowsAffected = jdbcTemplate.update(sql.toString(), parameters.toArray());
        
        log.debug("Updated {} root fields for aggregate: {}, rows affected: {}", 
                modifiedFields.size(), aggregateId, rowsAffected);
    }
    
    /**
     * Processes child entity changes (added, removed, modified).
     * 
     * @param aggregateChangeMetadata the aggregate change metadata
     */
    private void processChildEntityChanges(AggregateChangeMetadata aggregateChangeMetadata) {
        String aggregateId = aggregateChangeMetadata.getAggregateId();
        
        // Process added children
        for (AggregateChangeMetadata.ChildEntityChange childChange : aggregateChangeMetadata.getAddedChildren()) {
            insertChildEntity(aggregateId, childChange);
        }
        
        // Process removed children
        for (AggregateChangeMetadata.ChildEntityChange childChange : aggregateChangeMetadata.getRemovedChildren()) {
            deleteChildEntity(childChange);
        }
        
        // Process modified children
        for (AggregateChangeMetadata.ChildEntityChange childChange : aggregateChangeMetadata.getModifiedChildren()) {
            updateChildEntity(childChange);
        }
        
        log.debug("Processed child entity changes for aggregate: {}, added: {}, removed: {}, modified: {}", 
                aggregateId, 
                aggregateChangeMetadata.getAddedChildren().size(),
                aggregateChangeMetadata.getRemovedChildren().size(),
                aggregateChangeMetadata.getModifiedChildren().size());
    }
    
    /**
     * Updates entity fields based on field change metadata.
     * 
     * @param fieldChangeMetadata the field change metadata
     */
    private void updateEntityFields(FieldChangeMetadata fieldChangeMetadata) {
        String entityId = fieldChangeMetadata.getEntityId();
        String entityType = fieldChangeMetadata.getEntityType();
        Set<String> modifiedFields = fieldChangeMetadata.getModifiedFields();
        Map<String, Object> newValues = fieldChangeMetadata.getNewValues();
        
        if (modifiedFields.isEmpty()) {
            return;
        }
        
        // Build dynamic SQL for updating only changed fields
        String tableName = getTableName(entityType);
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        
        List<Object> parameters = new ArrayList<>();
        List<String> setClause = new ArrayList<>();
        
        for (String field : modifiedFields) {
            String columnName = convertToColumnName(field);
            setClause.add(columnName + " = ?");
            parameters.add(newValues.get(field));
        }
        
        sql.append(String.join(", ", setClause));
        sql.append(" WHERE id = ?");
        parameters.add(entityId);
        
        // Execute the update
        int rowsAffected = jdbcTemplate.update(sql.toString(), parameters.toArray());
        
        log.debug("Updated {} fields for entity: {}, rows affected: {}", 
                modifiedFields.size(), entityId, rowsAffected);
    }
    
    /**
     * Processes child field changes for aggregate roots.
     * 
     * @param fieldChangeMetadata the field change metadata
     */
    private void processChildFieldChanges(FieldChangeMetadata fieldChangeMetadata) {
        Map<String, FieldChangeMetadata> childChanges = fieldChangeMetadata.getChildChanges();
        
        for (Map.Entry<String, FieldChangeMetadata> entry : childChanges.entrySet()) {
            String childKey = entry.getKey();
            FieldChangeMetadata childChange = entry.getValue();
            
            if (childChange.hasModifiedFields()) {
                updateEntityFields(childChange);
                log.debug("Updated child entity: {} for parent: {}", 
                        childKey, fieldChangeMetadata.getEntityId());
            }
        }
    }
    
    /**
     * Inserts a new child entity.
     * 
     * @param aggregateId the parent aggregate ID
     * @param childChange the child entity change
     */
    private void insertChildEntity(String aggregateId, AggregateChangeMetadata.ChildEntityChange childChange) {
        String tableName = getTableName(childChange.getChildType());
        String childId = childChange.getChildId();
        
        // This is a simplified example - in reality, you'd need to map
        // the child entity fields to appropriate SQL insert statements
        String sql = "INSERT INTO " + tableName + " (id, parent_id, created_at) VALUES (?, ?, ?)";
        
        int rowsAffected = jdbcTemplate.update(sql, childId, aggregateId, Instant.now());
        
        log.debug("Inserted child entity: {} into table: {}, rows affected: {}", 
                childId, tableName, rowsAffected);
    }
    
    /**
     * Deletes a child entity.
     * 
     * @param childChange the child entity change
     */
    private void deleteChildEntity(AggregateChangeMetadata.ChildEntityChange childChange) {
        String tableName = getTableName(childChange.getChildType());
        String childId = childChange.getChildId();
        
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        
        int rowsAffected = jdbcTemplate.update(sql, childId);
        
        log.debug("Deleted child entity: {} from table: {}, rows affected: {}", 
                childId, tableName, rowsAffected);
    }
    
    /**
     * Updates a modified child entity.
     * 
     * @param childChange the child entity change
     */
    private void updateChildEntity(AggregateChangeMetadata.ChildEntityChange childChange) {
        if (!childChange.hasModifiedFields()) {
            return;
        }
        
        String tableName = getTableName(childChange.getChildType());
        String childId = childChange.getChildId();
        Set<String> modifiedFields = childChange.getModifiedFields();
        Map<String, Object> newValues = childChange.getNewValues();
        
        // Build dynamic SQL for updating only changed fields
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        
        List<Object> parameters = new ArrayList<>();
        List<String> setClause = new ArrayList<>();
        
        for (String field : modifiedFields) {
            String columnName = convertToColumnName(field);
            setClause.add(columnName + " = ?");
            parameters.add(newValues.get(field));
        }
        
        sql.append(String.join(", ", setClause));
        sql.append(" WHERE id = ?");
        parameters.add(childId);
        
        // Execute the update
        int rowsAffected = jdbcTemplate.update(sql.toString(), parameters.toArray());
        
        log.debug("Updated child entity: {} in table: {}, modified fields: {}, rows affected: {}", 
                childId, tableName, modifiedFields.size(), rowsAffected);
    }
    
    /**
     * Updates the last modified timestamp for an entity.
     * 
     * @param entityId the entity ID
     * @param entityType the entity type
     */
    private void updateLastModifiedTimestamp(String entityId, String entityType) {
        String tableName = getTableName(entityType);
        String sql = "UPDATE " + tableName + " SET last_modified = ? WHERE id = ?";
        
        int rowsAffected = jdbcTemplate.update(sql, Instant.now(), entityId);
        
        log.debug("Updated last_modified timestamp for entity: {} in table: {}, rows affected: {}", 
                entityId, tableName, rowsAffected);
    }
    
    /**
     * Converts entity type to database table name.
     * 
     * This method applies naming conventions to convert Java class names
     * to database table names (e.g., camelCase to snake_case).
     * 
     * @param entityType the entity type/class name
     * @return the corresponding database table name
     */
    private String getTableName(String entityType) {
        // Convert camelCase to snake_case and make lowercase
        // Example: Workflow -> workflow_aggregate
        return entityType.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    /**
     * Converts field name to database column name.
     * 
     * This method applies naming conventions to convert Java field names
     * to database column names.
     * 
     * @param fieldName the Java field name
     * @return the corresponding database column name
     */
    private String convertToColumnName(String fieldName) {
        // Convert camelCase to snake_case
        // Example: firstName -> first_name
        return fieldName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
