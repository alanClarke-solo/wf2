package ac.workflow.service;

import ac.workflow.domain.RouteConfig;
import ac.workflow.domain.WorkflowStatus;

import java.util.Map;

public interface EndpointService {
    String submitWorkflow(RouteConfig routeConfig, String workflowId, Map<String, Object> parameters);
    WorkflowStatus getWorkflowStatus(RouteConfig routeConfig, String externalId);
    String getEndpointType();
}
