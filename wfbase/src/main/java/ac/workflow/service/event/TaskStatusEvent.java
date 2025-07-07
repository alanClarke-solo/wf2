package ac.workflow.service.event;

import ac.workflow.domain.enums.TaskStatus;
import ac.workflow.domain.model.Task;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskStatusEvent extends ApplicationEvent {
    private final Task task;
    private final TaskStatus status;
    
    public TaskStatusEvent(Object source, Task task, TaskStatus status) {
        super(source);
        this.task = task;
        this.status = status;
    }
}