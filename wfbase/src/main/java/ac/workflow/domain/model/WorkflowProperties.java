package ac.workflow.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workflow_properties")
public class WorkflowProperties {
    @Id
    private Long propId;
    private Long workflowId;
    private String propValue;
}