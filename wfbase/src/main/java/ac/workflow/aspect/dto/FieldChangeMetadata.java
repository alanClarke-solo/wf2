package ac.workflow.aspect.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.time.Instant;
import java.util.*;

/**
 * Metadata container for field changes detected in an entity.
 *
 * This class captures comprehensive information about changes made to an entity,
 * including modified fields, old and new values, and child entity changes.
 * It provides null-safe operations and defensive programming practices.
 *
 * Key features:
 * - Null-safe collection operations
 * - Immutable getters to prevent external modification
 * - Utility methods for safe data manipulation
 * - Comprehensive change detection methods
 *
 * @author Workflow Team
 * @version 1.0
 */
@Data
@Builder
@Log4j2
public class FieldChangeMetadata {

    /**
     * Unique identifier of the entity that changed.
     * This field is required and cannot be null.
     */
    @NonNull
    private String entityId;

    /**
     * Type/class name of the entity that changed.
     * This field is required and cannot be null.
     */
    @NonNull
    private String entityType;

    /**
     * Set of field names that were modified.
     * Initialized as empty set to prevent null pointer exceptions.
     */
    @Builder.Default
    private Set<String> modifiedFields = new HashSet<>();

    /**
     * Map of field names to their old values before the change.
     * Initialized as empty map to prevent null pointer exceptions.
     */
    @Builder.Default
    private Map<String, Object> oldValues = new HashMap<>();

    /**
     * Map of field names to their new values after the change.
     * Initialized as empty map to prevent null pointer exceptions.
     */
    @Builder.Default
    private Map<String, Object> newValues = new HashMap<>();

    /**
     * Timestamp when the change was detected.
     * Set automatically during change detection.
     */
    private Instant changeTimestamp;

    /**
     * Flag indicating if this entity is an aggregate root.
     * Aggregate roots receive special handling for child changes.
     */
    private boolean isAggregateRoot;

    /**
     * Map of child entity changes keyed by child entity identifier.
     * Supports hierarchical change tracking for complex aggregates.
     */
    @Builder.Default
    private Map<String, FieldChangeMetadata> childChanges = new HashMap<>();

    /**
     * Checks if any changes were detected in this entity or its children.
     *
     * @return true if changes were detected, false otherwise
     */
    public boolean hasChanges() {
        return hasModifiedFields() || hasChildChanges();
    }

    /**
     * Checks if any fields were modified in this entity.
     *
     * @return true if fields were modified, false otherwise
     */
    public boolean hasModifiedFields() {
        return modifiedFields != null && !modifiedFields.isEmpty();
    }

    /**
     * Checks if any child entities have changes.
     *
     * @return true if child changes exist, false otherwise
     */
    public boolean hasChildChanges() {
        return childChanges != null && !childChanges.isEmpty();
    }

    /**
     * Gets the count of modified fields.
     *
     * @return number of modified fields
     */
    public int getModifiedFieldCount() {
        return modifiedFields != null ? modifiedFields.size() : 0;
    }

    /**
     * Gets the count of child entities with changes.
     *
     * @return number of child entities with changes
     */
    public int getChildChangeCount() {
        return childChanges != null ? childChanges.size() : 0;
    }

    // Defensive getters to prevent external modification

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
     * Gets an immutable view of child changes.
     *
     * @return unmodifiable map of child changes
     */
    public Map<String, FieldChangeMetadata> getChildChanges() {
        return childChanges != null ? Collections.unmodifiableMap(childChanges) : Collections.emptyMap();
    }

    // Setter methods with null safety and defensive copying

    /**
     * Sets modified fields with defensive copying.
     *
     * @param modifiedFields set of modified field names
     */
    public void setModifiedFields(Set<String> modifiedFields) {
        this.modifiedFields = modifiedFields != null ? new HashSet<>(modifiedFields) : new HashSet<>();
    }

    /**
     * Sets old values with defensive copying.
     *
     * @param oldValues map of old values
     */
    public void setOldValues(Map<String, Object> oldValues) {
        this.oldValues = oldValues != null ? new HashMap<>(oldValues) : new HashMap<>();
    }

    /**
     * Sets new values with defensive copying.
     *
     * @param newValues map of new values
     */
    public void setNewValues(Map<String, Object> newValues) {
        this.newValues = newValues != null ? new HashMap<>(newValues) : new HashMap<>();
    }

    /**
     * Sets child changes with defensive copying.
     *
     * @param childChanges map of child changes
     */
    public void setChildChanges(Map<String, FieldChangeMetadata> childChanges) {
        this.childChanges = childChanges != null ? new HashMap<>(childChanges) : new HashMap<>();
    }

    // Utility methods for safe modification

    /**
     * Adds a modified field name safely.
     *
     * @param fieldName name of the modified field
     */
    public void addModifiedField(String fieldName) {
        if (isValidFieldName(fieldName)) {
            ensureModifiedFieldsInitialized();
            this.modifiedFields.add(fieldName);
            log.debug("Added modified field: {} for entity: {}", fieldName, entityId);
        }
    }

    /**
     * Adds an old value for a field safely.
     *
     * @param fieldName name of the field
     * @param value old value of the field
     */
    public void addOldValue(String fieldName, Object value) {
        if (isValidFieldName(fieldName)) {
            ensureOldValuesInitialized();
            this.oldValues.put(fieldName, value);
            log.debug("Added old value for field: {} in entity: {}", fieldName, entityId);
        }
    }

    /**
     * Adds a new value for a field safely.
     *
     * @param fieldName name of the field
     * @param value new value of the field
     */
    public void addNewValue(String fieldName, Object value) {
        if (isValidFieldName(fieldName)) {
            ensureNewValuesInitialized();
            this.newValues.put(fieldName, value);
            log.debug("Added new value for field: {} in entity: {}", fieldName, entityId);
        }
    }

    /**
     * Adds a child change record safely.
     *
     * @param childKey unique key for the child entity
     * @param childChange change metadata for the child
     */
    public void addChildChange(String childKey, FieldChangeMetadata childChange) {
        if (isValidFieldName(childKey) && childChange != null) {
            ensureChildChangesInitialized();
            this.childChanges.put(childKey, childChange);
            log.debug("Added child change: {} for entity: {}", childKey, entityId);
        }
    }

    /**
     * Gets the old value for a specific field.
     *
     * @param fieldName name of the field
     * @return old value or null if not found
     */
    public Object getOldValue(String fieldName) {
        return oldValues != null ? oldValues.get(fieldName) : null;
    }

    /**
     * Gets the new value for a specific field.
     *
     * @param fieldName name of the field
     * @return new value or null if not found
     */
    public Object getNewValue(String fieldName) {
        return newValues != null ? newValues.get(fieldName) : null;
    }

    /**
     * Checks if a specific field was modified.
     *
     * @param fieldName name of the field to check
     * @return true if the field was modified, false otherwise
     */
    public boolean isFieldModified(String fieldName) {
        return modifiedFields != null && modifiedFields.contains(fieldName);
    }

    /**
     * Merges another FieldChangeMetadata into this one.
     * Useful for combining changes from multiple sources.
     *
     * @param other the other metadata to merge
     */
    public void merge(FieldChangeMetadata other) {
        if (other == null || !Objects.equals(this.entityId, other.entityId)) {
            return;
        }

        // Merge modified fields
        if (other.modifiedFields != null) {
            ensureModifiedFieldsInitialized();
            this.modifiedFields.addAll(other.modifiedFields);
        }

        // Merge old values
        if (other.oldValues != null) {
            ensureOldValuesInitialized();
            this.oldValues.putAll(other.oldValues);
        }

        // Merge new values
        if (other.newValues != null) {
            ensureNewValuesInitialized();
            this.newValues.putAll(other.newValues);
        }

        // Merge child changes
        if (other.childChanges != null) {
            ensureChildChangesInitialized();
            this.childChanges.putAll(other.childChanges);
        }

        log.debug("Merged field change metadata for entity: {}", entityId);
    }

    // Private helper methods

    private boolean isValidFieldName(String fieldName) {
        return fieldName != null && !fieldName.trim().isEmpty();
    }

    private void ensureModifiedFieldsInitialized() {
        if (this.modifiedFields == null) {
            this.modifiedFields = new HashSet<>();
        }
    }

    private void ensureOldValuesInitialized() {
        if (this.oldValues == null) {
            this.oldValues = new HashMap<>();
        }
    }

    private void ensureNewValuesInitialized() {
        if (this.newValues == null) {
            this.newValues = new HashMap<>();
        }
    }

    private void ensureChildChangesInitialized() {
        if (this.childChanges == null) {
            this.childChanges = new HashMap<>();
        }
    }

    /**
     * Creates a summary string of the changes for logging purposes.
     *
     * @return formatted summary of changes
     */
    public String getChangeSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Entity: ").append(entityType).append(":").append(entityId);
        summary.append(", Modified Fields: ").append(getModifiedFieldCount());
        summary.append(", Child Changes: ").append(getChildChangeCount());

        if (changeTimestamp != null) {
            summary.append(", Timestamp: ").append(changeTimestamp);
        }

        return summary.toString();
    }
}