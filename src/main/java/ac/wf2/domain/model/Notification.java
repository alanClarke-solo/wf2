package ac.wf2.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("notifications")
public class Notification {
    @Id
    private Long id;
    private Long workflowId;
    private Long statusId;
    private String sentYN;
}