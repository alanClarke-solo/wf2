package ac.workflow.domain.dto;

import lombok.Data;

@Data
public class WorkflowStartRequest {
    private String workflowConfigId;
    private String region;
}