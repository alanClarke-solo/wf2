package ac.workflow.domain.dto;

import lombok.Data;

@Data
public class TaskExecutionConfig {
    private String executionType; // REST, SHELL, INTERNAL
    private String endpoint;
    private String method;
    private String remoteHost;
    private String command;
    private boolean async = false;
    private int timeoutSeconds = 300;
}