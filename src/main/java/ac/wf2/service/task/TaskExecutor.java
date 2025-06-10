package ac.wf2.service.task;

import ac.wf2.domain.dto.TaskConfigDto;
import ac.wf2.domain.model.Task;

public interface TaskExecutor {
    boolean execute(Task task, TaskConfigDto config);
}