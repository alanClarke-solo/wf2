package ac.workflow.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table("task")
public class Task {
    @Id
    private Long taskId;
    private Long workflowId;
    private String externalTaskId;
    private Long taskDefId;
    private Long statusId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("scheduled_time")
    private OffsetDateTime scheduledTime;

    @Column("executed_time")
    private OffsetDateTime executedTime;
    private String updatedBy;
}