package ac.workflow.service.task;

import ac.workflow.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class TaskPredicateEvaluator {
    
    private final TaskRepository taskRepository;
    
    public boolean evaluateAll(List<String> preconditions, Long workflowId) {
        return preconditions.stream()
                .allMatch(condition -> evaluateCondition(condition, workflowId));
    }
    
    private boolean evaluateCondition(String condition, Long workflowId) {
        // Simple condition evaluation - can be extended
        if (condition.startsWith("task_completed:")) {
            String taskId = condition.substring("task_completed:".length());
            return isTaskCompleted(taskId, workflowId);
        }
        
        if (condition.startsWith("task_success:")) {
            String taskId = condition.substring("task_success:".length());
            return isTaskSuccessful(taskId, workflowId);
        }
        
        // Default evaluation
        log.warn("Unknown condition: {}", condition);
        return true;
    }
    
    private boolean isTaskCompleted(String taskId, Long workflowId) {
        return taskRepository.findByWorkflowId(workflowId)
                .stream()
                .anyMatch(task -> task.getExternalTaskId().equals(taskId) 
                        && task.getStatusId() >= 3L); // SUCCESS or FAILURE
    }
    
    private boolean isTaskSuccessful(String taskId, Long workflowId) {
        return taskRepository.findByWorkflowId(workflowId)
                .stream()
                .anyMatch(task -> task.getExternalTaskId().equals(taskId) 
                        && task.getStatusId().equals(3L)); // SUCCESS
    }
}