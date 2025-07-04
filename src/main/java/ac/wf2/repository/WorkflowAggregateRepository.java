package ac.wf2.repository;

import ac.wf2.domain.model.WorkflowAggregate;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowAggregateRepository extends CrudRepository<WorkflowAggregate, Long> {
    
    @Query("SELECT * FROM workflow WHERE status = :status")
    List<WorkflowAggregate> findByStatus(@Param("status") String status);
    
    @Query("SELECT COUNT(*) FROM task WHERE workflow_id = :workflowId")
    int countTasksByWorkflowId(@Param("workflowId") Long workflowId);
}
