package ac.wf2.domain.model;

import ac.wf2.aspect.annotation.TrackFieldChanges;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Table("workflow")
@TrackFieldChanges(isAggregateRoot = true, childEntities = {"tasks"}, deepComparison = true)
public class WorkflowAggregate {
    @Id
    private Long id;
    private String name;
    private String status;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    
    // Spring Data JDBC aggregate - children are loaded with parent
    @MappedCollection(idColumn = "workflow_id")
    @EqualsAndHashCode.Exclude
    private Set<TaskEntity> tasks = new HashSet<>();
    
    // Transient fields for change tracking
    @Transient
    private Set<TaskEntity> addedTasks = new HashSet<>();
    
    @Transient
    private Set<TaskEntity> removedTasks = new HashSet<>();
    
    @Transient
    private Set<TaskEntity> modifiedTasks = new HashSet<>();
    
    // Aggregate business methods
    public void addTask(TaskEntity task) {
        task.setWorkflowId(this.id);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        
        if (this.tasks.add(task)) {
            this.addedTasks.add(task);
            this.setUpdatedAt(Instant.now());
        }
    }
    
    public void removeTask(TaskEntity task) {
        if (this.tasks.remove(task)) {
            this.removedTasks.add(task);
            this.setUpdatedAt(Instant.now());
        }
    }
    
    public void updateTask(Long taskId, String newStatus, String newDescription) {
        TaskEntity task = findTaskById(taskId);
        if (task != null) {
            boolean modified = false;
            
            if (!newStatus.equals(task.getStatus())) {
                task.setStatus(newStatus);
                modified = true;
            }
            
            if (newDescription != null && !newDescription.equals(task.getDescription())) {
                task.setDescription(newDescription);
                modified = true;
            }
            
            if (modified) {
                task.setUpdatedAt(Instant.now());
                this.modifiedTasks.add(task);
                this.setUpdatedAt(Instant.now());
            }
        }
    }
    
    public TaskEntity findTaskById(Long taskId) {
        return tasks.stream()
            .filter(task -> task.getId().equals(taskId))
            .findFirst()
            .orElse(null);
    }
    
    public void markTaskAsModified(TaskEntity task) {
        task.setUpdatedAt(Instant.now());
        this.modifiedTasks.add(task);
        this.setUpdatedAt(Instant.now());
    }
    
    public boolean hasTaskChanges() {
        return !addedTasks.isEmpty() || !removedTasks.isEmpty() || !modifiedTasks.isEmpty();
    }
    
    public void clearChangeTracking() {
        addedTasks.clear();
        removedTasks.clear();
        modifiedTasks.clear();
    }
    
    // Business logic methods
    public void completeAllTasks() {
        tasks.forEach(task -> {
            if (!"COMPLETED".equals(task.getStatus())) {
                task.setStatus("COMPLETED");
                task.setUpdatedAt(Instant.now());
                modifiedTasks.add(task);
            }
        });
        this.setUpdatedAt(Instant.now());
    }
    
    public boolean hasCompletedTasks() {
        return tasks.stream().anyMatch(task -> "COMPLETED".equals(task.getStatus()));
    }
    
    public int getTaskCount() {
        return tasks.size();
    }
    
    public int getCompletedTaskCount() {
        return (int) tasks.stream()
            .filter(task -> "COMPLETED".equals(task.getStatus()))
            .count();
    }
}

