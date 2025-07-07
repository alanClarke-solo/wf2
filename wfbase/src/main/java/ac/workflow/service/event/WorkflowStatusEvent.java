package ac.workflow.service.event;

import ac.workflow.domain.enums.WorkflowStatus;
import ac.workflow.domain.model.Workflow;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WorkflowStatusEvent extends ApplicationEvent {
    private final Workflow workflow;
    private final WorkflowStatus status;
    
    public WorkflowStatusEvent(Object source, Workflow workflow, WorkflowStatus status) {
        super(source);
        this.workflow = workflow;
        this.status = status;
    }
}