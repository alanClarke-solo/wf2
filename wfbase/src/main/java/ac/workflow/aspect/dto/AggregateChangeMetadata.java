package ac.workflow.aspect.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.time.Instant;
import java.util.*;

/**
 * Metadata container for aggregate root changes.
 *
 * This class captures changes at the aggregate level, including both root entity
 * changes and child entity modifications. It provides a comprehensive view of
 * all changes within an aggregate boundary.
 *
 * Key features:
 * - Separate tracking of root and child changes
 * - Type-safe change categorization (added, removed, modified)
 * - Null-safe operations and defensive programming
 * - Comprehensive change detection methods
 *
 * @author Workflow Team
 * @version 1.0
 */
@Data
@Builder
@Log4j2
public class AggregateChangeMetadata {

    /**
     * Unique identifier of the aggregate root entity.
     * This field is required and cannot be null.
     */
    @NonNull
    private String aggregateId;

    /**
     * Type/class name of the aggregate root entity.
     * This field is required and cannot be null.
     */
    @NonNull
    private String aggregateType;

    /**
     * Set of root-level field names that were modified.
     * Initialized as empty set to prevent null pointer exceptions.
     */
    @Builder.Default
    private Set<String> modifiedRootFields = new HashSet<>();

    /**
     * Map of root-level field names to their old values.
     * Initialized as empty map to prevent null pointer exceptions.
     */
    @Builder.Default
    private Map<String, Object> oldRootValues = new HashMap<>();

    /**
     * Map of root-level field names to their new values.
     * Initialized as empty map to prevent null pointer exceptions.
     */
    @Builder.Default
    private Map<String, Object> newRootValues = new HashMap<>();

    /**
     * Timestamp when the aggregate changes were detected.
     * Set automatically during change detection.
     */
    private Instant changeTimestamp;

    /**
     * List of child entities that were added to the aggregate.
     * Initialized as empty list to prevent null pointer exceptions.
     */
    @Builder.Default
    private List<ChildEntityChange> addedChildren = new ArrayList<>();

    /**
     * List of child entities that were removed from the aggregate.
     * Initialized as empty list to prevent null pointer exceptions.
     */
    @Builder.Default
    private List<ChildEntityChange> removedChildren = new ArrayList<>();

    /**
     * List of child entities that were modified within the aggregate.
     * Initialized as empty list to prevent null pointer exceptions.
     */
    @Builder.Default
    private List<ChildEntityChange> modifiedChildren = new ArrayList<>();

    /**
     * Checks if any root-level fields were modified.
     *
     * @return true if root fields were modified, false otherwise
     */
    public boolean hasRootChanges() {
        return modifiedRootFields != null && !modifiedRootFields.isEmpty();
    }

    /**
     * Checks if any child entities have changes.
     *
     * @return true if child changes exist, false otherwise
     */
    public boolean hasChildChanges() {
        return (addedChildren != null && !addedChildren.isEmpty()) ||
                (removedChildren != null && !removedChildren.isEmpty()) ||
                (modifiedChildren != null && !modifiedChildren.isEmpty());
    }

    /**
     * Checks if any changes were detected in the aggregate.
     *
     * @return true if any changes were detected, false otherwise
     */
    public boolean hasAnyChanges() {
        return hasRootChanges() || hasChildChanges();
    }

    /**
     * Gets the total count of all changes (root + child).
     *
     * @return total number of changes
     */
    public int getTotalChangeCount() {
        int rootCount = modifiedRootFields != null ? modifiedRootFields.size() : 0;
        int addedCount = addedChildren != null ? addedChildren.size() : 0;
        int removedCount = removedChildren != null ? removedChildren.size() : 0;
        int modifiedCount = modifiedChildren != null ? modifiedChildren.size() : 0;

        return rootCount + addedCount + removedCount + modifiedCount;
    }

    // Defensive getters to prevent external modification

    /**
     * Gets an immutable view of modified root fields.
     *
     * @return unmodifiable set of modified root field names
     */
    public Set<String> getModifiedRootFields() {
        return modifiedRootFields != null ? Collections.unmodifiableSet(modifiedRootFields) : Collections.emptySet();
    }

    /**
     * Gets an immutable view of old root values.
     *
     * @return unmodifiable map of old root values
     */
    public Map<String, Object> getOldRootValues() {
        return oldRootValues != null ? Collections.unmodifiableMap(oldRootValues) : Collections.emptyMap();
    }

    /**
     * Gets an immutable view of new root values.
     *
     * @return unmodifiable map of new root values
     */
    public Map<String, Object> getNewRootValues() {
        return newRootValues != null ? Collections.unmodifiableMap(newRootValues) : Collections.emptyMap();
    }

    /**
     * Gets an immutable view of added children.
     *
     * @return unmodifiable list of added child entities
     */
    public List<ChildEntityChange> getAddedChildren() {
        return addedChildren != null ? Collections.unmodifiableList(addedChildren) : Collections.emptyList();
    }

    /**
     * Gets an immutable view of removed children.
     *
     * @return unmodifiable list of removed child entities
     */
    public List<ChildEntityChange> getRemovedChildren() {
        return removedChildren != null ? Collections.unmodifiableList(removedChildren) : Collections.emptyList();
    }

    /**
     * Gets an immutable view of modified children.
     *
     * @return unmodifiable list of modified child entities
     */
    public List<ChildEntityChange> getModifiedChildren() {
        return modifiedChildren != null ? Collections.unmodifiableList(modifiedChildren) : Collections.emptyList();
    }

    /**
     * Adds a child entity change record safely.
     *
     * @param childChange the child entity change to add
     */
    public void addChildChange(ChildEntityChange childChange) {
        if (childChange == null) {
            log.warn("Attempted to add null child change to aggregate: {}", aggregateId);
            return;
        }

        switch (childChange.getChangeType()) {
            case ADDED:
                if (addedChildren == null) {
                    addedChildren = new ArrayList<>();
                }
                addedChildren.add(childChange);
                break;
            case REMOVED:
                if (removedChildren == null) {
                    removedChildren = new ArrayList<>();
                }
                removedChildren.add(childChange);
                break;
            case MODIFIED:
                if (modifiedChildren == null) {
                    modifiedChildren = new ArrayList<>();
                }
                modifiedChildren.add(childChange);
                break;
            default:
                log.warn("Unknown change type: {} for child: {}", childChange.getChangeType(), childChange.getChildId());
        }

        log.debug("Added {} child change for entity: {} in aggregate: {}",
                childChange.getChangeType(), childChange.getChildId(), aggregateId);
    }

    /**
     * Creates a summary string of the aggregate changes for logging purposes.
     *
     * @return formatted summary of aggregate changes
     */
    public String getChangeSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Aggregate: ").append(aggregateType).append(":").append(aggregateId);
        summary.append(", Root Fields: ").append(hasRootChanges() ? modifiedRootFields.size() : 0);
        summary.append(", Added Children: ").append(addedChildren != null ? addedChildren.size() : 0);
        summary.append(", Removed Children: ").append(removedChildren != null ? removedChildren.size() : 0);
        summary.append(", Modified Children: ").append(modifiedChildren != null ? modifiedChildren.size() : 0);

        if (changeTimestamp != null) {
            summary.append(", Timestamp: ").append(changeTimestamp);
        }

        return summary.toString();
    }

    /**
     * Represents a change to a child entity within an aggregate.
     *
     * This inner class captures detailed information about individual child
     * entity changes, including the type of change and affected fields.
     */
    @Data
    @Builder
    public static class ChildEntityChange {

        /**
         * Unique identifier of the child entity.
         * This field is required and cannot be null.
         */
        @NonNull
        private String childId;

        /**
         * Type/class name of the child entity.
         * This field is required and cannot be null.
         */
        @NonNull
        private String childType;

        /**
         * Name of the field in the parent that holds this child entity.
         * May be null for direct child relationships.
         */
        private String childFieldName;

        /**
         * The actual child entity object.
         * May be null depending on the change type.
         */
        private Object childEntity;

        /**
         * Set of field names that were modified in the child entity.
         * Only populated for MODIFIED change types.
         */
        @Builder.Default
        private Set<String> modifiedFields = new HashSet<>();

        /**
         * Map of field names to their old values in the child entity.
         * Only populated for MODIFIED change types.
         */
        @Builder.Default
        private Map<String, Object> oldValues = new HashMap<>();

        /**
         * Map of field names to their new values in the child entity.
         * Only populated for MODIFIED change types.
         */
        @Builder.Default
        private Map<String, Object> newValues = new HashMap<>();

        /**
         * Type of change that occurred to this child entity.
         * This field is required and cannot be null.
         */
        @NonNull
        private ChangeType changeType;

        /**
         * Checks if any fields were modified in this child entity.
         *
         * @return true if fields were modified, false otherwise
         */
        public boolean hasModifiedFields() {
            return modifiedFields != null && !modifiedFields.isEmpty();
        }

        /**
         * Gets the count of modified fields in this child entity.
         *
         * @return number of modified fields
         */
        public int getModifiedFieldCount() {
            return modifiedFields != null ? modifiedFields.size() : 0;
        }

        // Defensive getters

        /**
         * Gets an immutable view of modified fields.
         *
         * @return unmodifiable set of modified field names
         */
        public Set<String> getModifiedFields() {
            return modifiedFields != null ? Collections.unmodifiableSet(modifiedFields) : Collections.emptySet();
        }

        /**
         * Gets an immutable view of old values.
         *
         * @return unmodifiable map of old values
         */
        public Map<String, Object> getOldValues() {
            return oldValues != null ? Collections.unmodifiableMap(oldValues) : Collections.emptyMap();
        }

        /**
         * Gets an immutable view of new values.
         *
         * @return unmodifiable map of new values
         */
        public Map<String, Object> getNewValues() {
            return newValues != null ? Collections.unmodifiableMap(newValues) : Collections.emptyMap();
        }

        /**
         * Creates a summary string of the child entity change.
         *
         * @return formatted summary of child entity change
         */
        public String getChangeSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("Child: ").append(childType).append(":").append(childId);
            summary.append(", Type: ").append(changeType);

            if (changeType == ChangeType.MODIFIED) {
                summary.append(", Modified Fields: ").append(getModifiedFieldCount());
            }

            if (childFieldName != null) {
                summary.append(", Field: ").append(childFieldName);
            }

            return summary.toString();
        }
    }

    /**
     * Enumeration of possible change types for child entities.
     */
    public enum ChangeType {
        /**
         * Child entity was added to the aggregate.
         */
        ADDED,

        /**
         * Child entity was removed from the aggregate.
         */
        REMOVED,

        /**
         * Child entity was modified within the aggregate.
         */
        MODIFIED
    }
}