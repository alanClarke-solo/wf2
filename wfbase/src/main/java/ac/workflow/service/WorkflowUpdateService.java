package ac.workflow.service;

import ac.workflow.aspect.annotation.TrackFieldChanges;
import ac.workflow.domain.dto.WorkflowUpdateRequest;
import ac.workflow.domain.enums.WorkflowStatus;
import ac.workflow.domain.model.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class WorkflowUpdateService {
    
    @TrackFieldChanges(isAggregateRoot = true, deepComparison = true)
    public void updateWorkflow(Workflow workflow, WorkflowUpdateRequest request) {
        // Apply updates to workflow object
        if (request.getStatusId() != null) {
            workflow.setStatusId(request.getStatusId());
        }
        if (request.getDescription() != null) {
            workflow.setDescription(request.getDescription());
        }
        if (request.getProperties() != null) {
            workflow.getProperties().putAll(request.getProperties());
        }
        
        // The AOP aspect will automatically detect changes and perform optimized updates
        log.info("Workflow update completed for ID: {}", workflow.getWorkflowId());
    }
    
    @TrackFieldChanges(deepComparison = false)
    public void updateWorkflowStatus(Workflow workflow, String newStatus) {
        // Simple field update
        workflow.setStatusId(WorkflowStatus.valueOf(newStatus).getId());
        
        // Only the status field will be updated in the database
        log.info("Workflow status updated to: {} for ID: {}", newStatus, workflow.getExternalWorkflowId());
    }
}
