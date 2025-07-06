package ac.workflow.service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WorkflowInterruptEvent extends ApplicationEvent {
    private final Long workflowId;
    private final String reason;
    
    public WorkflowInterruptEvent(Object source, Long workflowId, String reason) {
        super(source);
        this.workflowId = workflowId;
        this.reason = reason;
    }
}