package ac.wf2.service.cache;

import ac.wf2.domain.model.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowCacheService {
    
    private final CacheManager cacheManager;
    
    public void cacheRunningWorkflow(Workflow workflow) {
        Cache cache = cacheManager.getCache("runningWorkflows");
        if (cache != null) {
            cache.put(workflow.getWorkflowId(), workflow);
            log.debug("Cached running workflow: {}", workflow.getWorkflowId());
        }
    }
    
    public void moveToCompletedWorkflows(Workflow workflow) {
        // Remove from running cache
        Cache runningCache = cacheManager.getCache("runningWorkflows");
        if (runningCache != null) {
            runningCache.evict(workflow.getWorkflowId());
        }
        
        // Add to completed cache
        Cache completedCache = cacheManager.getCache("completedWorkflows");
        if (completedCache != null) {
            completedCache.put(workflow.getWorkflowId(), workflow);
            log.debug("Moved workflow to completed cache: {}", workflow.getWorkflowId());
        }
    }
    
    public void evictWorkflow(Long workflowId) {
        Cache runningCache = cacheManager.getCache("runningWorkflows");
        Cache completedCache = cacheManager.getCache("completedWorkflows");
        
        if (runningCache != null) {
            runningCache.evict(workflowId);
        }
        if (completedCache != null) {
            completedCache.evict(workflowId);
        }
        
        log.debug("Evicted workflow from all caches: {}", workflowId);
    }
}