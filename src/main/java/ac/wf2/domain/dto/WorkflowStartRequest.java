package ac.wf2.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class WorkflowStartRequest {
    @NotBlank
    private String workflowConfigId;
    @NotBlank
    private String region;
}