package ac.wf2.service.event;

import ac.wf2.domain.enums.TaskStatus;
import ac.wf2.domain.enums.WorkflowStatus;
import ac.wf2.domain.model.Task;
import ac.wf2.domain.model.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEventService {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public void publishWorkflowStatusEvent(Workflow workflow, WorkflowStatus status) {
        WorkflowStatusEvent event = new WorkflowStatusEvent(this, workflow, status);
        eventPublisher.publishEvent(event);
        log.debug("Published workflow status event: {} for workflow: {}", status, workflow.getWorkflowId());
    }
    
    public void publishTaskStatusEvent(Task task, TaskStatus status) {
        TaskStatusEvent event = new TaskStatusEvent(this, task, status);
        eventPublisher.publishEvent(event);
        log.debug("Published task status event: {} for task: {}", status, task.getTaskId());
    }
    
    public void publishWorkflowInterruptEvent(Long workflowId, String reason) {
        WorkflowInterruptEvent event = new WorkflowInterruptEvent(this, workflowId, reason);
        eventPublisher.publishEvent(event);
        log.info("Published workflow interrupt event for workflow: {}, reason: {}", workflowId, reason);
    }
    
    public void publishBroadcastInterruptEvent(String reason) {
        BroadcastInterruptEvent event = new BroadcastInterruptEvent(this, reason);
        eventPublisher.publishEvent(event);
        log.info("Published broadcast interrupt event, reason: {}", reason);
    }
}