package ac.wf2.service;

import ac.wf2.aspect.annotation.TrackFieldChanges;
import ac.wf2.domain.model.WorkflowAggregate;
import ac.wf2.domain.model.TaskEntity;
import ac.wf2.repository.WorkflowAggregateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedWorkflowAggregateService {
    
    private final WorkflowAggregateRepository workflowRepository;
    
    @Transactional(readOnly = true)
    public Optional<WorkflowAggregate> findById(Long id) {
        return workflowRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public List<WorkflowAggregate> findByStatus(String status) {
        return workflowRepository.findByStatus(status);
    }
    
    @Transactional
    public WorkflowAggregate createWorkflow(WorkflowAggregate workflow) {
        workflow.setCreatedAt(Instant.now());
        workflow.setUpdatedAt(Instant.now());
        
        // Set timestamps for all tasks
        workflow.getTasks().forEach(task -> {
            task.setCreatedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
        });
        
        return workflowRepository.save(workflow);
    }
    
    @TrackFieldChanges(isAggregateRoot = true, childEntities = {"tasks"}, deepComparison = true)
    @Transactional
    public void updateWorkflowStatus(WorkflowAggregate workflow, String newStatus) {
        // Only change status - AOP will detect and update only this field
        workflow.setStatus(newStatus);
        workflow.setUpdatedAt(Instant.now());
        
        log.info("Updated workflow status to: {} for ID: {}", newStatus, workflow.getId());
    }
    
    @TrackFieldChanges(isAggregateRoot = true, childEntities = {"tasks"}, deepComparison = true)
    @Transactional
    public void addTaskToWorkflow(WorkflowAggregate workflow, TaskEntity task) {
        // Use aggregate method - AOP will detect new child
        workflow.addTask(task);
        
        log.info("Added task to workflow: {}", workflow.getId());
    }
    
    @TrackFieldChanges(isAggregateRoot = true, childEntities = {"tasks"}, deepComparison = true)
    @Transactional
    public void updateTaskStatus(WorkflowAggregate workflow, Long taskId, String newStatus) {
        // Use aggregate method - AOP will detect child modification
        workflow.updateTask(taskId, newStatus, null);
        
        log.info("Updated task {} status to: {} in workflow: {}", taskId, newStatus, workflow.getId());
    }
    
    @TrackFieldChanges(isAggregateRoot = true, childEntities = {"tasks"}, deepComparison = true)
    @Transactional
    public void completeAllTasks(WorkflowAggregate workflow) {
        // Business operation - AOP will detect multiple child modifications
        workflow.completeAllTasks();
        
        log.info("Completed all tasks in workflow: {}", workflow.getId());
    }
    
    @TrackFieldChanges(isAggregateRoot = true, childEntities = {"tasks"}, deepComparison = true)
    @Transactional
    public void updateWorkflowDetails(WorkflowAggregate workflow, String newName, String newDescription) {
        // Multiple field update - AOP will detect and update only changed fields
        boolean changed = false;
        
        if (newName != null && !newName.equals(workflow.getName())) {
            workflow.setName(newName);
            changed = true;
        }
        
        if (newDescription != null && !newDescription.equals(workflow.getDescription())) {
            workflow.setDescription(newDescription);
            changed = true;
        }
        
        if (changed) {
            workflow.setUpdatedAt(Instant.now());
        }
        
        log.info("Updated workflow details for ID: {}", workflow.getId());
    }
    
    @Transactional
    public void deleteWorkflow(Long workflowId) {
        // Standard delete - no AOP needed
        workflowRepository.deleteById(workflowId);
        log.info("Deleted workflow: {}", workflowId);
    }
}
