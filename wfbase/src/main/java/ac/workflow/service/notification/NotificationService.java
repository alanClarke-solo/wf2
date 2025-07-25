package ac.workflow.service.notification;

import ac.workflow.domain.enums.WorkflowStatus;
import ac.workflow.domain.model.Notification;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.NotificationRepository;
import ac.workflow.service.event.WorkflowStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Log4j2
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

        // Record notification with UTC timestamp
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
            log.info("Notification sent at {} UTC: Workflow {} changed to status {}",
                    Instant.now(), workflow.getExternalWorkflowId(), status);
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
        // Use OffsetDateTime with UTC offset for database storage
        notification.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        notificationRepository.save(notification);
    }
}