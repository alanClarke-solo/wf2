package ac.wf2.service;

import ac.wf2.aspect.annotation.TrackFieldChanges;
import ac.wf2.domain.dto.WorkflowUpdateRequest;
import ac.wf2.domain.model.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowUpdateService {
    
    @TrackFieldChanges(isAggregateRoot = true, childEntities = {"tasks", "properties"}, deepComparison = true)
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
        workflow.setStatusId(newStatus);
        
        // Only the status field will be updated in the database
        log.info("Workflow status updated to: {} for ID: {}", newStatus, workflow.getId());
    }
}
