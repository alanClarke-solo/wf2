package ac.workflow.service;

import ac.workflow.domain.dto.TaskConfigDto;
import ac.workflow.domain.dto.WorkflowConfigDto;
import ac.workflow.domain.enums.TaskStatus;
import ac.workflow.domain.enums.WorkflowStatus;
import ac.workflow.domain.model.Task;
import ac.workflow.repository.TaskRepository;
import ac.workflow.repository.WorkflowRepository;
import ac.workflow.service.dag.WorkflowDAGService;
import ac.workflow.service.event.WorkflowEventService;
import ac.workflow.service.notification.NotificationService;
import ac.workflow.service.task.TaskExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Log4j2
public class WorkflowExecutionService {
    
    private final WorkflowRepository workflowRepository;
    private final TaskRepository taskRepository;
    private final WorkflowDAGService dagService;
    private final TaskExecutorService taskExecutorService;
    private final WorkflowEventService eventService;
    private final NotificationService notificationService;
    
    private final Map<Long, CompletableFuture<Void>> runningWorkflows = new ConcurrentHashMap<>();
    
    @Async
    public CompletableFuture<Void> executeWorkflowAsync(Long workflowId, WorkflowConfigDto config) {
        CompletableFuture<Void> execution = CompletableFuture.runAsync(() -> {
            try {
                executeWorkflow(workflowId, config);
            } catch (Exception e) {
                log.error("Workflow execution failed for workflow: {}", workflowId, e);
                handleWorkflowFailure(workflowId, e);
            }
        });
        
        runningWorkflows.put(workflowId, execution);
        return execution;
    }
    
    @Transactional
    private void executeWorkflow(Long workflowId, WorkflowConfigDto config) {
        log.info("Executing workflow: {}", workflowId);
        
        // Update workflow status to RUNNING
        updateWorkflowStatus(workflowId, WorkflowStatus.RUNNING);
        
        // Build DAG from configuration
        var dag = dagService.buildDAG(config);
        
        // Execute tasks in topological order
        List<String> executionOrder = dagService.getTopologicalOrder(dag);
        
        for (String taskId : executionOrder) {
            if (isWorkflowStopped(workflowId)) {
                log.info("Workflow {} was stopped, terminating execution", workflowId);
                break;
            }
            
            TaskConfigDto taskConfig = findTaskConfig(config, taskId);
            if (taskConfig == null) {
                log.warn("Task configuration not found for taskId: {}", taskId);
                continue;
            }
            
            // Check preconditions
            if (!taskExecutorService.evaluatePreconditions(taskConfig, workflowId)) {
                if (!taskConfig.isForceExecution()) {
                    log.info("Skipping task {} due to precondition failure", taskId);
                    continue;
                }
            }
            
            // Execute task
            boolean success = executeTask(workflowId, taskConfig);
            
            if (!success && taskConfig.isFailureStopsWorkflow()) {
                log.error("Task {} failed and is configured to stop workflow", taskId);
                updateWorkflowStatus(workflowId, WorkflowStatus.FAILURE);
                return;
            }
        }
        
        // Check if all mandatory tasks completed successfully
        if (allMandatoryTasksCompleted(workflowId, config)) {
            updateWorkflowStatus(workflowId, WorkflowStatus.SUCCESS);
        } else {
            updateWorkflowStatus(workflowId, WorkflowStatus.FAILURE);
        }
        
        runningWorkflows.remove(workflowId);
    }
    
    private boolean executeTask(Long workflowId, TaskConfigDto taskConfig) {
        try {
            // Create a task record
            Task task = createTaskRecord(workflowId, taskConfig);
            
            // Execute task
            boolean success = taskExecutorService.executeTask(task, taskConfig);
            
            // Update task status
            TaskStatus finalStatus = success ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
            updateTaskStatus(task.getTaskId(), finalStatus);
            
            return success;
            
        } catch (Exception e) {
            log.error("Task execution failed: {}", taskConfig.getTaskId(), e);
            return false;
        }
    }

    // Update scheduling and execution timestamps
    public void scheduleTask(Task task, Duration delay) {
        task.setScheduledTime(OffsetDateTime.now(ZoneOffset.UTC).plus(delay));
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        taskRepository.save(task);
    }

    public void executeTask(Task task) {
        task.setExecutedTime(OffsetDateTime.now(ZoneOffset.UTC));
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        taskRepository.save(task);
    }

    
    private Task createTaskRecord(Long workflowId, TaskConfigDto taskConfig) {
        Task task = new Task();
        task.setWorkflowId(workflowId);
        task.setExternalTaskId(taskConfig.getTaskId());
        task.setStatusId(TaskStatus.STARTING.getId());
        task.setStartTime(OffsetDateTime.now());
        task.setUpdatedBy("SYSTEM");
        task.setUpdatedAt(OffsetDateTime.now());
        
        return taskRepository.save(task);
    }
    
    private void updateWorkflowStatus(Long workflowId, WorkflowStatus status) {
        workflowRepository.findById(workflowId).ifPresent(workflow -> {
            workflow.setStatusId(status.getId());
            workflow.setUpdatedAt(OffsetDateTime.now());
            if (status == WorkflowStatus.SUCCESS || status == WorkflowStatus.FAILURE) {
                workflow.setEndTime(OffsetDateTime.now());
            }
            workflowRepository.save(workflow);
            
            // Send notification
            notificationService.sendWorkflowStatusNotification(workflow, status);
        });
    }
    
    private void updateTaskStatus(Long taskId, TaskStatus status) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatusId(status.getId());
            task.setUpdatedAt(OffsetDateTime.now());
            if (status == TaskStatus.SUCCESS || status == TaskStatus.FAILURE) {
                task.setEndTime(OffsetDateTime.now());
            }
            taskRepository.save(task);
        });
    }
    
    public void stopWorkflow(Long workflowId, boolean immediate) {
        CompletableFuture<Void> execution = runningWorkflows.get(workflowId);
        if (execution != null) {
            if (immediate) {
                execution.cancel(true);
            }
            runningWorkflows.remove(workflowId);
        }
    }
    
    private boolean isWorkflowStopped(Long workflowId) {
        return !runningWorkflows.containsKey(workflowId);
    }
    
    private TaskConfigDto findTaskConfig(WorkflowConfigDto config, String taskId) {
        return config.getTasks().stream()
                .filter(task -> task.getTaskId().equals(taskId))
                .findFirst()
                .orElse(null);
    }
    
    private boolean allMandatoryTasksCompleted(Long workflowId, WorkflowConfigDto config) {
        List<Task> tasks = taskRepository.findByWorkflowId(workflowId);
        
        for (TaskConfigDto taskConfig : config.getTasks()) {
            if (taskConfig.isMandatory()) {
                boolean found = tasks.stream()
                        .anyMatch(task -> task.getExternalTaskId().equals(taskConfig.getTaskId()) 
                                && task.getStatusId().equals(TaskStatus.SUCCESS.getId()));
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private void handleWorkflowFailure(Long workflowId, Exception e) {
        updateWorkflowStatus(workflowId, WorkflowStatus.FAILURE);
        runningWorkflows.remove(workflowId);
    }
}