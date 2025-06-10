package ac.wf2.domain.dto;

import lombok.Data;

@Data
public class WorkflowStopRequest {
    private boolean immediate = false;
}