package ac.wf2.domain.dto;

import lombok.Data;

@Data
public class TaskDependency {
    private String taskId;
    private String dependsOn;
    private String condition;
}