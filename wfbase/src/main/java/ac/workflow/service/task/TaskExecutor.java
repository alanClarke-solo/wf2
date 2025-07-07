package ac.workflow.service.task;

import ac.workflow.domain.dto.TaskConfigDto;
import ac.workflow.domain.model.Task;

public interface TaskExecutor {
    boolean execute(Task task, TaskConfigDto config);
}