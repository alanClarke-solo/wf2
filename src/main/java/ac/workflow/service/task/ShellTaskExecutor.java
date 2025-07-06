package ac.workflow.service.task;

import ac.workflow.domain.dto.TaskConfigDto;
import ac.workflow.domain.model.Task;
import com.jcraft.jsch.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Component
@Log4j2
public class ShellTaskExecutor implements TaskExecutor {
    
    @Override
    public boolean execute(Task task, TaskConfigDto config) {
        var executionConfig = config.getExecutionConfig();
        
        if (executionConfig.isAsync()) {
            return executeAsync(task, config);
        } else {
            return executeSync(task, config);
        }
    }
    
    private boolean executeSync(Task task, TaskConfigDto config) {
        var executionConfig = config.getExecutionConfig();
        
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession("user", executionConfig.getRemoteHost(), 22);
            session.setPassword("password"); // Should be configurable/secure
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(executionConfig.getCommand());
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            
            channel.setOutputStream(outputStream);
            channel.setErrStream(errorStream);
            
            channel.connect();
            
            // Wait for command to complete
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }
            
            int exitCode = channel.getExitStatus();
            String output = outputStream.toString();
            String error = errorStream.toString();
            
            log.info("Shell command executed for task: {}, exit code: {}", 
                    config.getTaskId(), exitCode);
            log.debug("Output: {}", output);
            
            if (!error.isEmpty()) {
                log.warn("Error output: {}", error);
            }
            
            channel.disconnect();
            session.disconnect();
            
            return exitCode == 0;
            
        } catch (Exception e) {
            log.error("Shell task execution failed for task: {}", config.getTaskId(), e);
            return false;
        }
    }
    
    private boolean executeAsync(Task task, TaskConfigDto config) {
        CompletableFuture.supplyAsync(() -> executeSync(task, config))
            .thenAccept(result -> {
                log.info("Async shell task {} completed with result: {}", 
                        config.getTaskId(), result);
            })
            .exceptionally(throwable -> {
                log.error("Async shell task {} failed", config.getTaskId(), throwable);
                return null;
            });
        
        return true;
    }
}