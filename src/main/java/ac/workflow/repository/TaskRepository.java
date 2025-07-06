package ac.workflow.repository;

import ac.workflow.domain.model.Task;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends CrudRepository<Task, Long> {
    
    List<Task> findByWorkflowId(Long workflowId);
    
    Optional<Task> findByExternalTaskId(String externalTaskId);
    
    @Query("SELECT t.* FROM task t WHERE t.workflow_id = :workflowId AND t.status_id = :statusId")
    List<Task> findByWorkflowIdAndStatusId(@Param("workflowId") Long workflowId, 
                                          @Param("statusId") Long statusId);
}