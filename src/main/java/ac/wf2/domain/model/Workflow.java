package ac.wf2.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("workflow")
public class Workflow {
    @Id
    private Long workflowId;
    private String externalWorkflowId;
    private Long statusId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String updatedBy;
    private OffsetDateTime updatedAt;
}