package ac.workflow.repository;

import ac.workflow.domain.model.Notification;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends CrudRepository<Notification, Long> {
    Optional<Notification> findByWorkflowIdAndStatusId(Long workflowId, Long statusId);
}