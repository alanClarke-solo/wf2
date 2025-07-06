package ac.workflow.domain.dto;

import lombok.Data;

@Data
public class WorkflowStopRequest {
    private boolean immediate = false;
}