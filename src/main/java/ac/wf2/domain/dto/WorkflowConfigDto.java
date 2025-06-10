package ac.wf2.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowConfigDto {
    private String workflowId;
    private String name;
    private String description;
    private String region;
    private String schedule;
    private Map<String, Object> properties;
    private List<String> protectedProperties;
    private List<TaskConfigDto> tasks;
    private List<TaskDependency> dependencies;
}