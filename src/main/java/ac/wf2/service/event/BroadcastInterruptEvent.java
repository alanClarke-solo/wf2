package ac.wf2.service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class BroadcastInterruptEvent extends ApplicationEvent {
    private final String reason;
    
    public BroadcastInterruptEvent(Object source, String reason) {
        super(source);
        this.reason = reason;
    }
}