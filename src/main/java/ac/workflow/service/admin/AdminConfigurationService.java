package ac.workflow.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Log4j2
public class AdminConfigurationService {
    
    private final CacheManager cacheManager;
    private final Map<String, Object> runtimeConfiguration = new ConcurrentHashMap<>();
    
    public void updateExecutionParameters(Map<String, Object> parameters) {
        log.info("Updating execution parameters: {}", parameters);
        
        parameters.forEach((key, value) -> {
            runtimeConfiguration.put("execution." + key, value);
            log.debug("Updated execution parameter: {} = {}", key, value);
        });
        
        // Clear relevant caches when execution parameters change
        clearExecutionRelatedCaches();
    }
    
    public void updateCacheProperties(Map<String, Object> properties) {
        log.info("Updating cache properties: {}", properties);
        
        properties.forEach((key, value) -> {
            runtimeConfiguration.put("cache." + key, value);
            log.debug("Updated cache property: {} = {}", key, value);
        });
        
        // Note: Some cache properties may require application restart to take effect
    }
    
    public void updateSchedules(Map<String, String> schedules) {
        log.info("Updating schedules: {}", schedules);
        
        schedules.forEach((key, value) -> {
            runtimeConfiguration.put("schedule." + key, value);
            log.debug("Updated schedule: {} = {}", key, value);
        });
    }
    
    public void updateWorkflowProperties(String workflowId, Map<String, Object> properties) {
        log.info("Updating workflow properties for {}: {}", workflowId, properties);
        
        properties.forEach((key, value) -> {
            String configKey = "workflow." + workflowId + "." + key;
            runtimeConfiguration.put(configKey, value);
            log.debug("Updated workflow property: {} = {}", configKey, value);
        });
        
        // Clear workflow-specific caches
        clearWorkflowCaches(workflowId);
    }
    
    public void updateTaskProperties(String taskId, Map<String, Object> properties) {
        log.info("Updating task properties for {}: {}", taskId, properties);
        
        properties.forEach((key, value) -> {
            String configKey = "task." + taskId + "." + key;
            runtimeConfiguration.put(configKey, value);
            log.debug("Updated task property: {} = {}", configKey, value);
        });
    }
    
    public Object getConfigurationValue(String key) {
        return runtimeConfiguration.get(key);
    }
    
    public Map<String, Object> getAllConfiguration() {
        return Map.copyOf(runtimeConfiguration);
    }
    
    private void clearExecutionRelatedCaches() {
        if (cacheManager.getCache("runningWorkflows") != null) {
            cacheManager.getCache("runningWorkflows").clear();
        }
        log.debug("Cleared execution-related caches");
    }
    
    private void clearWorkflowCaches(String workflowId) {
        // Clear caches that might be affected by workflow property changes
        if (cacheManager.getCache("workflowSearchResults") != null) {
            cacheManager.getCache("workflowSearchResults").clear();
        }
        if (cacheManager.getCache("workflowConfigs") != null) {
            cacheManager.getCache("workflowConfigs").evict(workflowId);
        }
        log.debug("Cleared workflow-specific caches for: {}", workflowId);
    }
}