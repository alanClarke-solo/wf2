package ac.wf2.service.monitoring;

import ac.wf2.aspect.dto.AggregateChangeMetadata;
import ac.wf2.domain.model.TaskEntity;
import ac.wf2.domain.model.WorkflowAggregate;
import ac.wf2.util.JacksonDeepCloner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregateChangeDetectorService {

    @Autowired
    private JacksonDeepCloner deepCloner;

    private final Map<String, WorkflowAggregate> aggregateSnapshots = new ConcurrentHashMap<>();

    public void captureAggregateSnapshot(WorkflowAggregate aggregate) {
        String aggregateKey = generateAggregateKey(aggregate);
        try {
            WorkflowAggregate snapshot = deepCloner.deepClone(aggregate);
            aggregateSnapshots.put(aggregateKey, snapshot);
            log.debug("Captured aggregate snapshot for: {}", aggregateKey);
        } catch (Exception e) {
            log.error("Failed to capture aggregate snapshot: {}", aggregateKey, e);
        }
    }

    public AggregateChangeMetadata detectAggregateChanges(WorkflowAggregate currentAggregate) {
        String aggregateKey = generateAggregateKey(currentAggregate);
        WorkflowAggregate originalSnapshot = aggregateSnapshots.get(aggregateKey);

        if (originalSnapshot == null) {
            log.warn("No snapshot found for aggregate: {}", aggregateKey);
            return AggregateChangeMetadata.builder()
                    .aggregateId(aggregateKey)
                    .aggregateType("WorkflowAggregate")
                    .changeTimestamp(Instant.now())
                    .build();
        }

        return compareAggregates(originalSnapshot, currentAggregate, aggregateKey);
    }

    private String generateAggregateKey(WorkflowAggregate aggregate) {
        if (aggregate == null || aggregate.getId() == null) {
            throw new IllegalArgumentException("Aggregate and its ID cannot be null");
        }
        return "WorkflowAggregate:" + aggregate.getId();
    }

    public void clearAggregateSnapshot(WorkflowAggregate aggregate) {
        String aggregateKey = generateAggregateKey(aggregate);
        aggregateSnapshots.remove(aggregateKey);
        log.debug("Cleared aggregate snapshot: {}", aggregateKey);
    }

    private AggregateChangeMetadata compareAggregates(WorkflowAggregate original,
                                                      WorkflowAggregate current, String aggregateKey) {

        // Compare root fields
        Set<String> modifiedRootFields = new HashSet<>();
        Map<String, Object> oldRootValues = new HashMap<>();
        Map<String, Object> newRootValues = new HashMap<>();

        compareRootFields(original, current, modifiedRootFields, oldRootValues, newRootValues);

        // Compare child entities
        List<AggregateChangeMetadata.ChildEntityChange> addedChildren = new ArrayList<>();
        List<AggregateChangeMetadata.ChildEntityChange> removedChildren = new ArrayList<>();
        List<AggregateChangeMetadata.ChildEntityChange> modifiedChildren = new ArrayList<>();

        compareChildEntities(original.getTasks(), current.getTasks(),
                addedChildren, removedChildren, modifiedChildren);

        return AggregateChangeMetadata.builder()
                .aggregateId(aggregateKey)
                .aggregateType("WorkflowAggregate")
                .modifiedRootFields(modifiedRootFields)
                .oldRootValues(oldRootValues)
                .newRootValues(newRootValues)
                .addedChildren(addedChildren)
                .removedChildren(removedChildren)
                .modifiedChildren(modifiedChildren)
                .changeTimestamp(Instant.now())
                .build();
    }

    private void compareRootFields(WorkflowAggregate original, WorkflowAggregate current,
                                   Set<String> modifiedFields, Map<String, Object> oldValues,
                                   Map<String, Object> newValues) {

        if (!Objects.equals(original.getName(), current.getName())) {
            modifiedFields.add("name");
            oldValues.put("name", original.getName());
            newValues.put("name", current.getName());
        }

        if (!Objects.equals(original.getStatus(), current.getStatus())) {
            modifiedFields.add("status");
            oldValues.put("status", original.getStatus());
            newValues.put("status", current.getStatus());
        }

        if (!Objects.equals(original.getDescription(), current.getDescription())) {
            modifiedFields.add("description");
            oldValues.put("description", original.getDescription());
            newValues.put("description", current.getDescription());
        }

        if (!Objects.equals(original.getUpdatedAt(), current.getUpdatedAt())) {
            modifiedFields.add("updatedAt");
            oldValues.put("updatedAt", original.getUpdatedAt());
            newValues.put("updatedAt", current.getUpdatedAt());
        }
    }

    private void compareChildEntities(Set<TaskEntity> originalTasks, Set<TaskEntity> currentTasks,
                                      List<AggregateChangeMetadata.ChildEntityChange> added,
                                      List<AggregateChangeMetadata.ChildEntityChange> removed,
                                      List<AggregateChangeMetadata.ChildEntityChange> modified) {

        Map<Long, TaskEntity> originalTasksMap = originalTasks.stream()
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(TaskEntity::getId, t -> t));

        Map<Long, TaskEntity> currentTasksMap = currentTasks.stream()
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(TaskEntity::getId, t -> t));

        // Find added tasks
        for (TaskEntity currentTask : currentTasks) {
            if (currentTask.getId() == null || !originalTasksMap.containsKey(currentTask.getId())) {
                added.add(AggregateChangeMetadata.ChildEntityChange.builder()
                        .childId(currentTask.getId() != null ? currentTask.getId().toString() : "new")
                        .childType("TaskEntity")
                        .childFieldName("tasks")
                        .childEntity(currentTask)
                        .changeType(AggregateChangeMetadata.ChangeType.ADDED)
                        .build());
            }
        }

        // Find removed tasks
        for (TaskEntity originalTask : originalTasks) {
            if (originalTask.getId() != null && !currentTasksMap.containsKey(originalTask.getId())) {
                removed.add(AggregateChangeMetadata.ChildEntityChange.builder()
                        .childId(originalTask.getId().toString())
                        .childType("TaskEntity")
                        .childFieldName("tasks")
                        .changeType(AggregateChangeMetadata.ChangeType.REMOVED)
                        .build());
            }
        }

        // Find modified tasks
        for (TaskEntity currentTask : currentTasks) {
            if (currentTask.getId() != null && originalTasksMap.containsKey(currentTask.getId())) {
                TaskEntity originalTask = originalTasksMap.get(currentTask.getId());

                Set<String> modifiedFields = new HashSet<>();
                Map<String, Object> oldValues = new HashMap<>();
                Map<String, Object> newValues = new HashMap<>();

                compareTaskFields(originalTask, currentTask, modifiedFields, oldValues, newValues);

                if (!modifiedFields.isEmpty()) {
                    modified.add(AggregateChangeMetadata.ChildEntityChange.builder()
                            .childId(currentTask.getId().toString())
                            .childType("TaskEntity")
                            .childFieldName("tasks")
                            .childEntity(currentTask)
                            .modifiedFields(modifiedFields)
                            .oldValues(oldValues)
                            .newValues(newValues)
                            .changeType(AggregateChangeMetadata.ChangeType.MODIFIED)
                            .build());
                }
            }
        }
    }

    private void compareTaskFields(TaskEntity original, TaskEntity current,
                                   Set<String> modifiedFields, Map<String, Object> oldValues,
                                   Map<String, Object> newValues) {

        if (!Objects.equals(original.getName(), current.getName())) {
            modifiedFields.add("name");
            oldValues.put("name", original.getName());
            newValues.put("name", current.getName());
        }

        if (!Objects.equals(original.getStatus(), current.getStatus())) {
            modifiedFields.add("status");
            oldValues.put("status", original.getStatus());
            newValues.put("status", current.getStatus());
        }

        if (!Objects.equals(original.getDescription(), current.getDescription())) {
            modifiedFields.add("description");
            oldValues.put("description", original.getDescription());
            newValues.put("description", current.getDescription());
        }

        if (!Objects.equals(original.getOrderIndex(), current.getOrderIndex())) {
            modifiedFields.add("orderIndex");
            oldValues.put("orderIndex", original.getOrderIndex());
            newValues.put("orderIndex", current.getOrderIndex());
        }

        if (!Objects.equals(original.getUpdatedAt(), current.getUpdatedAt())) {
            modifiedFields.add("updatedAt");
            oldValues.put("updatedAt", original.getUpdatedAt());
            newValues.put("updatedAt", current.getUpdatedAt());
        }
    }
}