package ac.wf2.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("task")
public class Task {
    @Id
    private Long taskId;
    private Long workflowId;
    private String externalTaskId;
    private Long taskDefId;
    private Long statusId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String updatedBy;
    private LocalDateTime updatedAt;
}