package ac.wf2.service.task;

import ac.wf2.domain.dto.TaskConfigDto;
import ac.wf2.domain.model.Task;
import ac.wf2.service.task.RestTaskExecutor;
import ac.wf2.service.task.ShellTaskExecutor;
import ac.wf2.service.task.TaskExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskExecutorService {
    
    private final Map<String, TaskExecutor> executors = new ConcurrentHashMap<>();
    private final RestTaskExecutor restTaskExecutor;
    private final ShellTaskExecutor shellTaskExecutor;
    private final TaskPredicateEvaluator predicateEvaluator;
    
    public boolean executeTask(Task task, TaskConfigDto config) {
        log.info("Executing task: {} of type: {}", config.getTaskId(), config.getType());
        
        TaskExecutor executor = getExecutor(config.getType());
        if (executor == null) {
            log.error("No executor found for task type: {}", config.getType());
            return false;
        }
        
        try {
            return executor.execute(task, config);
        } catch (Exception e) {
            log.error("Task execution failed for task: {}", config.getTaskId(), e);
            return false;
        }
    }
    
    public boolean evaluatePreconditions(TaskConfigDto config, Long workflowId) {
        if (config.getPreconditions() == null || config.getPreconditions().isEmpty()) {
            return true;
        }
        
        return predicateEvaluator.evaluateAll(config.getPreconditions(), workflowId);
    }
    
    private TaskExecutor getExecutor(String type) {
        if (executors.isEmpty()) {
            initializeExecutors();
        }
        return executors.get(type.toUpperCase());
    }
    
    private void initializeExecutors() {
        executors.put("REST", restTaskExecutor);
        executors.put("SHELL", shellTaskExecutor);
    }
}