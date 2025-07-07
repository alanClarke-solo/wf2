package ac.workflow.repository;

import ac.workflow.domain.model.Workflow;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRepository extends CrudRepository<Workflow, Long> {
    
    Optional<Workflow> findByExternalWorkflowId(String externalWorkflowId);
    
    List<Workflow> findByStatusId(Long statusId);
    
    @Query("SELECT w.* FROM workflow w " +
           "JOIN workflow_properties wp ON w.workflow_id = wp.workflow_id " +
           "WHERE wp.prop_value = :propertyValue")
    List<Workflow> findByProperty(@Param("propertyValue") String propertyValue);
    
    @Query("SELECT w.* FROM workflow w WHERE w.status_id IN (1, 2)")
    List<Workflow> findRunningWorkflows();

//    @Query("SELECT * FROM workflow WHERE status = :status")
//    List<Workflow> findByStatus(@Param("status") String status);
//
//    @Query("SELECT COUNT(*) FROM task WHERE workflow_id = :workflowId")
//    int countTasksByWorkflowId(@Param("workflowId") Long workflowId);

}