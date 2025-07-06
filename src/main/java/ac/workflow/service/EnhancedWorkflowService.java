package ac.workflow.service;

import ac.workflow.aspect.annotation.TrackFieldChanges;
import ac.workflow.domain.model.Task;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log4j2
public class EnhancedWorkflowService {

    private final WorkflowRepository workflowRepository;

    @Transactional(readOnly = true)
    public Optional<Workflow> findById(Long id) {
        return workflowRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Workflow> findByStatusId(Long statusId) {
        return workflowRepository.findByStatusId(statusId);
    }

    @Transactional
    public Workflow createWorkflow(Workflow workflow) {
        workflow.setCreatedAt(OffsetDateTime.now());
        workflow.setUpdatedAt(OffsetDateTime.now());

        // Set timestamps for all tasks
        if (workflow.getTasks() != null) {
            workflow.getTasks().forEach(task -> {
                task.setCreatedAt(OffsetDateTime.now());
                task.setUpdatedAt(OffsetDateTime.now());
            });
        }

        return workflowRepository.save(workflow);
    }

    @TrackFieldChanges(isAggregateRoot = true, deepComparison = true)
    @Transactional
    public Workflow updateWorkflowStatus(Workflow workflow, Long newStatusId) {
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow cannot be null");
        }

        workflow.setStatusId(newStatusId);
        workflow.setUpdatedAt(OffsetDateTime.now());

        Workflow savedWorkflow = workflowRepository.save(workflow);
        log.info("Updated workflow status to: {} for ID: {}", newStatusId, workflow.getWorkflowId());
        return savedWorkflow;
    }

    @TrackFieldChanges(isAggregateRoot = true, deepComparison = true)
    @Transactional
    public Workflow addTaskToWorkflow(Workflow workflow, Task task) {
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }

        workflow.addTask(task);

        Workflow savedWorkflow = workflowRepository.save(workflow);
        log.info("Added task to workflow: {}", workflow.getWorkflowId());
        return savedWorkflow;
    }

    @TrackFieldChanges(isAggregateRoot = true, deepComparison = true)
    @Transactional
    public Workflow updateWorkflowDetails(Workflow workflow, String newName, String newDescription) {
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow cannot be null");
        }

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
            workflow.setUpdatedAt(OffsetDateTime.now());
            Workflow savedWorkflow = workflowRepository.save(workflow);
            log.info("Updated workflow details for ID: {}", workflow.getWorkflowId());
            return savedWorkflow;
        }

        return workflow;
    }

    @Transactional
    public void deleteWorkflow(Long workflowId) {
        workflowRepository.deleteById(workflowId);
        log.info("Deleted workflow: {}", workflowId);
    }
}