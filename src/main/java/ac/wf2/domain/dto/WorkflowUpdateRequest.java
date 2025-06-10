package ac.wf2.domain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class WorkflowUpdateRequest {
    private Map<String, Object> properties;
}