package ac.workflow.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskConfigDto {
    private String taskId;
    private String name;
    private String description;
    private String type;
    private boolean mandatory = true;
    private boolean forceExecution = false;
    private boolean failureStopsWorkflow = true;
    private String schedule;
    private boolean forceExecutionAfterPrevious = false;
    private boolean forceExecutionIfSchedulePassed = false;
    private Map<String, Object> inputParameters;
    private List<String> preconditions;
    private TaskExecutionConfig executionConfig;
}