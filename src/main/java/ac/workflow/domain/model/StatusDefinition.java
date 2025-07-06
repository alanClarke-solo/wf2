package ac.workflow.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("status_def")
public class StatusDefinition {
    @Id
    private Long statusId;
    private String displayName;
}