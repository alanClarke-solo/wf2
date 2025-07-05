package ac.wf2.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Table("WORKFLOW")
public class Workflow {
    @Id
    private Long workflowId;
    private String externalWorkflowId;
    private Long statusId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String description;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;

    @MappedCollection(idColumn = "WORKFLOW_ID")
    private Set<Task> tasks = new HashSet<>();

}