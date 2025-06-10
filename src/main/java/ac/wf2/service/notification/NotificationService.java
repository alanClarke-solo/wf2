package ac.wf2.service.notification;

import ac.wf2.domain.enums.WorkflowStatus;
import ac.wf2.domain.model.Notification;
import ac.wf2.domain.model.Workflow;
import ac.wf2.repository.NotificationRepository;
import ac.wf2.service.event.WorkflowStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    
    @Async
    public void sendWorkflowStatusNotification(Workflow workflow, WorkflowStatus status) {
        log.info("Sending notification for workflow: {}, status: {}", 
                workflow.getWorkflowId(), status);
        
        // Check if notification already sent
        if (isNotificationAlreadySent(workflow.getWorkflowId(), status.getId())) {
            log.debug("Notification already sent for workflow: {}, status: {}", 
                    workflow.getWorkflowId(), status);
            return;
        }
        
        // Send notification (implementation depends on notification system)
        boolean sent = sendNotification(workflow, status);
        
        // Record notification
        recordNotification(workflow.getWorkflowId(), status.getId(), sent);
    }
    
    @EventListener
    @Async
    public void handleWorkflowStatusEvent(WorkflowStatusEvent event) {
        sendWorkflowStatusNotification(event.getWorkflow(), event.getStatus());
    }
    
    private boolean sendNotification(Workflow workflow, WorkflowStatus status) {
        try {
            // Implementation for actual notification sending
            // This could be email, SMS, webhook, etc.
            log.info("Notification sent: Workflow {} changed to status {}", 
                    workflow.getExternalWorkflowId(), status);
            return true;
        } catch (Exception e) {
            log.error("Failed to send notification for workflow: {}", workflow.getWorkflowId(), e);
            return false;
        }
    }
    
    private boolean isNotificationAlreadySent(Long workflowId, Long statusId) {
        return notificationRepository.findByWorkflowIdAndStatusId(workflowId, statusId)
                .map(notification -> "Y".equals(notification.getSentYN()))
                .orElse(false);
    }
    
    private void recordNotification(Long workflowId, Long statusId, boolean sent) {
        Notification notification = new Notification();
        notification.setWorkflowId(workflowId);
        notification.setStatusId(statusId);
        notification.setSentYN(sent ? "Y" : "N");
        
        notificationRepository.save(notification);
    }
}