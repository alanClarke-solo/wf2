package ac.workflow.domain;

import java.time.LocalDateTime;
import java.util.Map;

public class WorkflowSubmission {
    private String submissionId;
    private String routeId;
    private String workflowId;
    private Map<String, Object> parameters;
    private WorkflowStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime lastUpdated;
    private String errorMessage;
    private Map<String, Object> result;
    
    // Constructors
    public WorkflowSubmission() {}
    
    public WorkflowSubmission(String routeId, String workflowId, Map<String, Object> parameters) {
        this.routeId = routeId;
        this.workflowId = workflowId;
        this.parameters = parameters;
        this.status = WorkflowStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }
    
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }
    
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
}
