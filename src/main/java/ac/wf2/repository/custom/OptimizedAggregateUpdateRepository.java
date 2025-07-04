package ac.wf2.repository.custom;

import ac.wf2.aspect.dto.AggregateChangeMetadata;
import ac.wf2.domain.model.TaskEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Slf4j
public class OptimizedAggregateUpdateRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Transactional
    public void updateAggregateSelectively(AggregateChangeMetadata changeMetadata) {
        if (!changeMetadata.hasAnyChanges()) {
            log.debug("No changes detected for aggregate: {}", changeMetadata.getAggregateId());
            return;
        }
        
        String aggregateId = extractIdFromKey(changeMetadata.getAggregateId());
        
        // Update root fields if changed
        if (changeMetadata.hasRootChanges()) {
            updateRootFields(aggregateId, changeMetadata);
        }
        
        // Handle child changes
        if (changeMetadata.hasChildChanges()) {
            handleChildChanges(aggregateId, changeMetadata);
        }
        
        log.info("Selectively updated aggregate: {}", changeMetadata.getAggregateId());
    }
    
    private void updateRootFields(String aggregateId, AggregateChangeMetadata changeMetadata) {
        Set<String> modifiedFields = changeMetadata.getModifiedRootFields();
        
        StringBuilder sql = new StringBuilder("UPDATE workflow SET ");
        List<Object> parameters = new ArrayList<>();
        
        List<String> setClauses = new ArrayList<>();
        for (String field : modifiedFields) {
            String columnName = toSnakeCase(field);
            setClauses.add(columnName + " = ?");
            parameters.add(changeMetadata.getNewRootValues().get(field));
        }
        
        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE id = ?");
        parameters.add(Long.valueOf(aggregateId));
        
        try {
            int updatedRows = jdbcTemplate.update(sql.toString(), parameters.toArray());
            log.info("Updated {} root fields for aggregate: {}, affected rows: {}", 
                    modifiedFields.size(), aggregateId, updatedRows);
        } catch (Exception e) {
            log.error("Failed to update root fields for aggregate: {}", aggregateId, e);
            throw new RuntimeException("Failed to update aggregate root fields", e);
        }
    }
    
    private void handleChildChanges(String aggregateId, AggregateChangeMetadata changeMetadata) {
        // Handle added children
        if (changeMetadata.getAddedChildren() != null) {
            for (AggregateChangeMetadata.ChildEntityChange childChange : changeMetadata.getAddedChildren()) {
                insertChild(aggregateId, childChange);
            }
        }
        
        // Handle removed children
        if (changeMetadata.getRemovedChildren() != null) {
            for (AggregateChangeMetadata.ChildEntityChange childChange : changeMetadata.getRemovedChildren()) {
                deleteChild(childChange.getChildId());
            }
        }
        
        // Handle modified children
        if (changeMetadata.getModifiedChildren() != null) {
            for (AggregateChangeMetadata.ChildEntityChange childChange : changeMetadata.getModifiedChildren()) {
                updateChild(childChange);
            }
        }
    }
    
    private void insertChild(String aggregateId, AggregateChangeMetadata.ChildEntityChange childChange) {
        if (!"TaskEntity".equals(childChange.getChildType())) {
            return;
        }
        
        TaskEntity task = (TaskEntity) childChange.getChildEntity();
        String sql = "INSERT INTO task (workflow_id, name, status, description, order_index, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try {
            int insertedRows = jdbcTemplate.update(sql,
                Long.valueOf(aggregateId),
                task.getName(),
                task.getStatus(),
                task.getDescription(),
                task.getOrderIndex(),
                task.getCreatedAt(),
                task.getUpdatedAt());
            
            log.info("Inserted new task for aggregate: {}, affected rows: {}", aggregateId, insertedRows);
        } catch (Exception e) {
            log.error("Failed to insert child task for aggregate: {}", aggregateId, e);
            throw new RuntimeException("Failed to insert child task", e);
        }
    }
    
    private void deleteChild(String childId) {
        String sql = "DELETE FROM task WHERE id = ?";
        
        try {
            int deletedRows = jdbcTemplate.update(sql, Long.valueOf(childId));
            log.info("Deleted task: {}, affected rows: {}", childId, deletedRows);
        } catch (Exception e) {
            log.error("Failed to delete child task: {}", childId, e);
            throw new RuntimeException("Failed to delete child task", e);
        }
    }
    
    private void updateChild(AggregateChangeMetadata.ChildEntityChange childChange) {
        if (!"TaskEntity".equals(childChange.getChildType()) || 
            childChange.getModifiedFields() == null || 
            childChange.getModifiedFields().isEmpty()) {
            return;
        }
        
        Set<String> modifiedFields = childChange.getModifiedFields();
        
        StringBuilder sql = new StringBuilder("UPDATE task SET ");
        List<Object> parameters = new ArrayList<>();
        
        List<String> setClauses = new ArrayList<>();
        for (String field : modifiedFields) {
            String columnName = toSnakeCase(field);
            setClauses.add(columnName + " = ?");
            parameters.add(childChange.getNewValues().get(field));
        }
        
        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE id = ?");
        parameters.add(Long.valueOf(childChange.getChildId()));
        
        try {
            int updatedRows = jdbcTemplate.update(sql.toString(), parameters.toArray());
            log.info("Updated {} fields for task: {}, affected rows: {}", 
                    modifiedFields.size(), childChange.getChildId(), updatedRows);
        } catch (Exception e) {
            log.error("Failed to update child task: {}", childChange.getChildId(), e);
            throw new RuntimeException("Failed to update child task", e);
        }
    }
    
    private String extractIdFromKey(String aggregateKey) {
        return aggregateKey.substring(aggregateKey.lastIndexOf(":") + 1);
    }
    
    private String toSnakeCase(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
