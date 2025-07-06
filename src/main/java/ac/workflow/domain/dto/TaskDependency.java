package ac.workflow.domain.dto;

import lombok.Data;

@Data
public class TaskDependency {
    private String taskId;
    private String dependsOn;
    private String condition;
}