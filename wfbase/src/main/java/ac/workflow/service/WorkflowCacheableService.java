package ac.workflow.service;

import ac.workflow.domain.enums.WorkflowStatus;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.WorkflowRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Service that demonstrates integration between @Cacheable and hierarchical cache
 */
@Service
public class WorkflowCacheableService {
    
    private final WorkflowRepository workflowRepository;
    private final RedissonHierarchicalCacheService<Workflow> hierarchicalCacheService;
    
    public WorkflowCacheableService(WorkflowRepository workflowRepository,
                                   RedissonHierarchicalCacheService<Workflow> hierarchicalCacheService) {
        this.workflowRepository = workflowRepository;
        this.hierarchicalCacheService = hierarchicalCacheService;
    }
    
    /**
     * Get workflow by ID - first tries hierarchical cache, then falls back to @Cacheable
     */
    @Cacheable(value = "workflowById", key = "#workflowId")
    public Optional<Workflow> getWorkflowById(String workflowId) {
        // First, try to find in hierarchical cache
        Optional<Workflow> hierarchicalResult = findInHierarchicalCache(workflowId);
        if (hierarchicalResult.isPresent()) {
            return hierarchicalResult;
        }
        
        // Fallback to database
        return workflowRepository.findByExternalWorkflowId(workflowId);
    }
    
    /**
     * Get workflows by status - integrates with hierarchical cache
     */
    @Cacheable(value = "workflowsByStatus", key = "#status.name()")
    public List<Workflow> getWorkflowsByStatus(WorkflowStatus status) {
        // Try hierarchical cache first
        List<String> hierarchy = Arrays.asList("workflows", status.name().toLowerCase());
        Optional<Workflow> hierarchicalResult = hierarchicalCacheService.get(hierarchy);
        
        if (hierarchicalResult.isPresent()) {
            // If we have hierarchical data, get all children
            return hierarchicalCacheService.getChildrenData(hierarchy)
                    .values()
                    .stream()
                    .toList();
        }
        
        // Fallback to database
        return workflowRepository.findByStatusId(status.getId());
    }
    
    /**
     * Get workflows by region and status - uses hierarchical cache efficiently
     */
    @Cacheable(value = "workflowsByRegion", key = "#region + '_' + #status.name()")
    public List<Workflow> getWorkflowsByRegionAndStatus(String region, WorkflowStatus status) {
        // Try hierarchical cache
        List<String> hierarchy = Arrays.asList("workflows", status.name().toLowerCase(), region);
        
        // Get all children workflows for this region and status
        return hierarchicalCacheService.getChildrenData(hierarchy)
                .values()
                .stream()
                .toList();
    }
    
    /**
     * Helper method to find workflow in hierarchical cache by trying different hierarchy levels
     */
    private Optional<Workflow> findInHierarchicalCache(String workflowId) {
        // Try to find workflow in different hierarchy levels
        for (WorkflowStatus status : WorkflowStatus.values()) {
            // Try pattern: workflows -> status -> region -> route -> workflowId
            // We don't know region and route, so we'll search in the status level
            List<String> statusHierarchy = Arrays.asList("workflows", status.name().toLowerCase());
            
            // Get all children data for this status
            var childrenData = hierarchicalCacheService.getChildrenData(statusHierarchy);
            
            // Look for our workflow in the children
            for (Workflow workflow : childrenData.values()) {
                if (workflowId.equals(workflow.getExternalWorkflowId())) {
                    return Optional.of(workflow);
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Bridge method to get data by dataId from hierarchical cache
     * This allows @Cacheable methods to reuse hierarchical cached data
     */
    public Optional<Workflow> getWorkflowByDataId(String dataId) {
        return hierarchicalCacheService.getDataById(dataId);
    }
    
    /**
     * Store workflow in both hierarchical cache and prepare for @Cacheable
     */
    public void cacheWorkflow(Workflow workflow, String region, String routeId) {
        WorkflowStatus status = WorkflowStatus.fromId(workflow.getStatusId());
        
        // Store in hierarchical cache
        hierarchicalCacheService.put(
            Arrays.asList("workflows", status.name().toLowerCase(), region, routeId, workflow.getExternalWorkflowId()),
            workflow,
            java.time.Duration.ofMinutes(30)
        );
        
        // The data is now available for @Cacheable methods through the Spring Cache integration
        // No need to explicitly cache in @Cacheable caches - they'll find the data automatically
    }
}
