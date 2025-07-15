
package ac.workflow.service.cache;

import ac.workflow.domain.enums.WorkflowStatus;
import ac.workflow.domain.model.Workflow;
import ac.workflow.service.RedissonHierarchicalCacheService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workflow-specific cache service that uses hierarchical caching
 */
@Service
public class WorkflowCacheService {
    
    private final RedissonHierarchicalCacheService<Workflow> hierarchicalCacheService;
    
    public WorkflowCacheService(RedissonHierarchicalCacheService<Workflow> hierarchicalCacheService) {
        this.hierarchicalCacheService = hierarchicalCacheService;
    }
    
    /**
     * Cache a running workflow
     */
    public void cacheRunningWorkflow(Workflow workflow) {
        cacheWorkflow(workflow, "default", "default", WorkflowStatus.RUNNING);
    }
    
    /**
     * Move workflow from running to completed cache
     */
    public void moveToCompletedWorkflows(Workflow workflow) {
        // Remove from running workflows
        invalidateWorkflowByStatus(workflow, WorkflowStatus.RUNNING);
        
        // Add to completed workflows
        cacheWorkflow(workflow, "default", "default", WorkflowStatus.fromId(workflow.getStatusId()));
    }
    
    /**
     * Cache workflow at appropriate hierarchy levels
     */
    public void cacheWorkflow(Workflow workflow, String region, String routeId, WorkflowStatus status) {
        Duration ttl = determineTtl(status);
        
        // Cache at multiple hierarchy levels
        List<String> hierarchy = Arrays.asList(
            "workflows", 
            status.name().toLowerCase(), 
            region, 
            routeId, 
            workflow.getExternalWorkflowId()
        );
        
        hierarchicalCacheService.put(hierarchy, workflow, ttl);
    }
    
    /**
     * Get workflow by ID with hierarchical fallback
     */
    public Optional<Workflow> getWorkflow(String workflowId, String region, String routeId, WorkflowStatus status) {
        List<String> hierarchy = Arrays.asList(
            "workflows", 
            status.name().toLowerCase(), 
            region, 
            routeId, 
            workflowId
        );
        
        return hierarchicalCacheService.findInHierarchy(hierarchy);
    }
    
    /**
     * Get all workflows for a region and status
     */
    public Map<List<String>, Workflow> getWorkflowsByRegionAndStatus(String region, WorkflowStatus status) {
        List<String> hierarchy = Arrays.asList(
            "workflows", 
            status.name().toLowerCase(), 
            region
        );
        
        return hierarchicalCacheService.getChildrenData(hierarchy);
    }
    
    /**
     * Invalidate workflow cache when status changes
     */
    public void invalidateWorkflowByStatus(Workflow workflow, WorkflowStatus oldStatus) {
        List<String> hierarchy = Arrays.asList(
            "workflows", 
            oldStatus.name().toLowerCase()
        );
        
        hierarchicalCacheService.invalidateHierarchyLevel(hierarchy, false);
    }
    
    /**
     * Get cache statistics
     */
    public void printCacheStatistics() {
        var stats = hierarchicalCacheService.getStatistics();
        System.out.println("Workflow Cache Statistics: " + stats);
    }
    
    private Duration determineTtl(WorkflowStatus status) {
        return switch (status) {
            case RUNNING, STARTING -> Duration.ofMinutes(55);
            case SUCCESS, FAILURE -> Duration.ofHours(2);
            default -> Duration.ofMinutes(15);
        };
    }
}
