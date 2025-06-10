package ac.wf2.service.task;

import ac.wf2.domain.dto.TaskConfigDto;
import ac.wf2.domain.model.Task;
import ac.wf2.service.lock.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestTaskExecutor implements TaskExecutor {
    
    private final RestTemplate restTemplate;
    private final DistributedLockService lockService;
    
    @Override
    public boolean execute(Task task, TaskConfigDto config) {
        String lockKey = "task_" + task.getTaskId();
        
        try {
            // Acquire distributed lock if needed
            if (requiresLock(config)) {
                if (!lockService.tryLock(lockKey, 30, TimeUnit.SECONDS)) {
                    log.warn("Failed to acquire lock for task: {}", config.getTaskId());
                    return false;
                }
            }
            
            if (config.getExecutionConfig().isAsync()) {
                return executeAsync(task, config);
            } else {
                return executeSync(task, config);
            }
            
        } finally {
            if (requiresLock(config)) {
                lockService.unlock(lockKey);
            }
        }
    }
    
    private boolean executeSync(Task task, TaskConfigDto config) {
        try {
            var executionConfig = config.getExecutionConfig();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Object> entity = new HttpEntity<>(config.getInputParameters(), headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                executionConfig.getEndpoint(),
                HttpMethod.valueOf(executionConfig.getMethod()),
                entity,
                String.class
            );
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            log.info("REST task {} completed with status: {}", 
                    config.getTaskId(), response.getStatusCode());
            
            return success;
            
        } catch (Exception e) {
            log.error("REST task execution failed for task: {}", config.getTaskId(), e);
            return false;
        }
    }
    
    private boolean executeAsync(Task task, TaskConfigDto config) {
        CompletableFuture.supplyAsync(() -> executeSync(task, config))
            .thenAccept(result -> {
                log.info("Async REST task {} completed with result: {}", 
                        config.getTaskId(), result);
            })
            .exceptionally(throwable -> {
                log.error("Async REST task {} failed", config.getTaskId(), throwable);
                return null;
            });
        
        return true; // For async, we return true immediately
    }
    
    private boolean requiresLock(TaskConfigDto config) {
        return config.getInputParameters() != null && 
               Boolean.TRUE.equals(config.getInputParameters().get("requiresLock"));
    }
}