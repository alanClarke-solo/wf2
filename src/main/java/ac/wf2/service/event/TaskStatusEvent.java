package ac.wf2.service.event;

import ac.wf2.domain.enums.TaskStatus;
import ac.wf2.domain.model.Task;
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