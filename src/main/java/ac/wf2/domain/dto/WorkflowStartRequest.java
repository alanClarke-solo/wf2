package ac.wf2.domain.dto;

import lombok.Data;

@Data
public class WorkflowStartRequest {
    private String workflowConfigId;
    private String region;
}