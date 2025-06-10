package ac.wf2.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("workflow")
public class Workflow {
    @Id
    private Long workflowId;
    private String externalWorkflowId;
    private Long statusId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String updatedBy;
    private LocalDateTime updatedAt;
}