package ac.workflow.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("notifications")
public class Notification {
    @Id
    private Long id;
    private Long workflowId;
    private Long statusId;
    private String sentYN;
    @Column("created_at")
    private OffsetDateTime createdAt;

}