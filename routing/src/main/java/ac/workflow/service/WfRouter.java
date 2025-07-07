package ac.workflow.service;

import ac.workflow.config.RouterConfigService;
import ac.workflow.domain.RouteConfig;
import ac.workflow.domain.WorkflowStatus;
import ac.workflow.domain.WorkflowSubmission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WfRouter {
    
    @Autowired
    private RouterConfigService configService;
    
    @Autowired
    private WorkflowSubmissionService submissionService;
    
    @Autowired
    private EndpointServiceFactory endpointServiceFactory;
    
    @Autowired
    private CacheService cacheService;
    
    public String submitWorkflow(String routeId, String workflowId, Map<String, Object> parameters) {
        // Validate route exists
        RouteConfig routeConfig = configService.getRouteConfig(routeId);
        if (routeConfig == null) {
            throw new IllegalArgumentException("Route not found: " + routeId);
        }
        
        // Create submission
        WorkflowSubmission submission = new WorkflowSubmission(routeId, workflowId, parameters);
        submission.setSubmissionId(UUID.randomUUID().toString());
        
        // Save to a database
        submissionService.saveSubmission(submission);
        
        // Get the appropriate endpoint service
        EndpointService endpointService = endpointServiceFactory.getEndpointService(routeConfig.getEndpointType());
        
        try {
            // Submit to external endpoint
            String externalId = endpointService.submitWorkflow(routeConfig, workflowId, parameters);
            
            // Update submission with external ID
            submission.setStatus(WorkflowStatus.QUEUED);
            submission.setLastUpdated(LocalDateTime.now());
            submission.getResult().put("externalId", externalId);
            
            submissionService.updateSubmission(submission);
            cacheService.cacheSubmission(submission);
            
            return submission.getSubmissionId();
            
        } catch (Exception e) {
            submission.setStatus(WorkflowStatus.FAILED);
            submission.setErrorMessage(e.getMessage());
            submission.setLastUpdated(LocalDateTime.now());
            submissionService.updateSubmission(submission);
            
            throw new RuntimeException("Failed to submit workflow", e);
        }
    }
    
    public WorkflowSubmission getSubmissionStatus(String submissionId) {
        // Try cache first
        WorkflowSubmission submission = cacheService.getCachedSubmission(submissionId);
        if (submission != null && shouldUseCachedResult(submission)) {
            return submission;
        }
        
        // Get from database
        submission = submissionService.getSubmission(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("Submission not found: " + submissionId);
        }
        
        // If completed, return a cached/db result
        if (submission.getStatus() == WorkflowStatus.COMPLETED || 
            submission.getStatus() == WorkflowStatus.FAILED ||
            submission.getStatus() == WorkflowStatus.CANCELLED) {
            return submission;
        }
        
        // For pending workflows, check if we should fetch fresh status
        if (shouldFetchFreshStatus(submission)) {
            refreshSubmissionStatus(submission);
        }
        
        return submission;
    }
    
    public List<WorkflowSubmission> getSubmissionsByPeriod(LocalDateTime from, LocalDateTime to, Map<String, Object> commonParams) {
        return submissionService.getSubmissionsByPeriod(from, to, commonParams);
    }
    
    private boolean shouldUseCachedResult(WorkflowSubmission submission) {
        // Use a cached result if the workflow is completed or if within threshold
        return submission.getStatus() == WorkflowStatus.COMPLETED || 
               submission.getStatus() == WorkflowStatus.FAILED ||
               submission.getStatus() == WorkflowStatus.CANCELLED ||
               !shouldFetchFreshStatus(submission);
    }
    
    private boolean shouldFetchFreshStatus(WorkflowSubmission submission) {
        RouteConfig routeConfig = configService.getRouteConfig(submission.getRouteId());
        if (routeConfig == null) {
            return false;
        }
        
        LocalDateTime threshold = submission.getLastUpdated().plusMinutes(routeConfig.getStatusThresholdMinutes());
        return LocalDateTime.now().isAfter(threshold);
    }
    
    private void refreshSubmissionStatus(WorkflowSubmission submission) {
        try {
            RouteConfig routeConfig = configService.getRouteConfig(submission.getRouteId());
            EndpointService endpointService = endpointServiceFactory.getEndpointService(routeConfig.getEndpointType());
            
            WorkflowStatus newStatus = endpointService.getWorkflowStatus(routeConfig, 
                (String) submission.getResult().get("externalId"));
            
            if (newStatus != submission.getStatus()) {
                submission.setStatus(newStatus);
                submission.setLastUpdated(LocalDateTime.now());
                
                submissionService.updateSubmission(submission);
                cacheService.cacheSubmission(submission);
            }
        } catch (Exception e) {
            // Log error but don't fail the request
            System.err.println("Failed to refresh status for submission: " + submission.getSubmissionId());
        }
    }
}
