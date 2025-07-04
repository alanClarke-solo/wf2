package ac.wf2.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("task")
public class TaskEntity {
    @Id
    private Long id;
    private Long workflowId;
    private String name;
    private String status;
    private String description;
    private Integer orderIndex;
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskEntity)) return false;
        TaskEntity task = (TaskEntity) o;
        return id != null && id.equals(task.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
