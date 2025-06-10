package ac.wf2.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workflow_prop_def")
public class WorkflowPropertyDefinition {
    @Id
    private Long propId;
    private String internalName;
    private String displayName;
}