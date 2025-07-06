package ac.workflow.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Workflow entity serving as both a database entity and aggregate root.
 *
 * This class combines database persistence capabilities with domain logic
 * and field change monitoring. It serves as the single source of truth
 * for workflow data throughout the application.
 *
 * @author Workflow Team
 * @version 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table("WORKFLOW")
public class Workflow {

    /**
     * Primary key for database persistence.
     */
    @Id
    private Long workflowId;

    /**
     * External identifier for workflow tracking.
     */
    private String externalWorkflowId;

    /**
     * Human-readable name of the workflow.
     */
    private String name;

    /**
     * Reference to workflow status.
     */
    private Long statusId;

    /**
     * Detailed description of the workflow.
     */
    private String description;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("start_time")
    private OffsetDateTime startTime;

    @Column("end_time")
    private OffsetDateTime endTime;

    // Helper methods for UTC handling
    public void setCreatedAtNow() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * User who last updated the workflow.
     */
    private String updatedBy;

    /**
     * Tasks associated with this workflow.
     * Maps to child table via foreign key.
     */
    @MappedCollection(idColumn = "WORKFLOW_ID")
    @Builder.Default
    private Set<Task> tasks = new HashSet<>();

    /**
     * Additional configuration properties.
     * Can be stored as JSON in database or separate table.
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Workflow properties for additional metadata.
     */
    @Builder.Default
    private List<WorkflowProperties> workflowProperties = new ArrayList<>();

    // Domain logic methods

    /**
     * Adds a task to the workflow.
     *
     * @param task the task to add
     */
    public void addTask(Task task) {
        if (task != null) {
            if (this.tasks == null) {
                this.tasks = new HashSet<>();
            }
            this.tasks.add(task);
        }
    }

    /**
     * Removes a task from the workflow.
     *
     * @param task the task to remove
     * @return true if the task was removed, false otherwise
     */
    public boolean removeTask(Task task) {
        if (this.tasks != null) {
            return this.tasks.remove(task);
        }
        return false;
    }

    /**
     * Sets a configuration property.
     *
     * @param key the property key
     * @param value the property value
     */
    public void setProperty(String key, Object value) {
        if (key != null && !key.trim().isEmpty()) {
            if (this.properties == null) {
                this.properties = new HashMap<>();
            }
            this.properties.put(key, value);
        }
    }

    /**
     * Gets a configuration property.
     *
     * @param key the property key
     * @return the property value or null if not found
     */
    public Object getProperty(String key) {
        return this.properties != null ? this.properties.get(key) : null;
    }

    /**
     * Checks if the workflow is currently running.
     *
     * @return true if workflow is in running state
     */
    public boolean isRunning() {
        // Add logic based on your WorkflowStatus enum
        return statusId != null && startTime != null && endTime == null;
    }

    /**
     * Marks the workflow as completed.
     */
    public void complete() {
        this.endTime = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        // Set appropriate status
    }
}
