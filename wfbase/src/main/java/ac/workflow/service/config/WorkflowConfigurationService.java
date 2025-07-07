package ac.workflow.service.config;

import ac.workflow.domain.dto.WorkflowConfigDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class WorkflowConfigurationService {
    
    private final ObjectMapper objectMapper;
    
    @Value("${workflow.config.folder:./config/workflows}")
    private String configFolder;
    
    private final Map<String, WorkflowConfigDto> configCache = new HashMap<>();
    
    @Cacheable("workflowConfigs")
    public WorkflowConfigDto getWorkflowConfig(String workflowId) {
        log.debug("Loading workflow configuration for: {}", workflowId);
        
        if (configCache.containsKey(workflowId)) {
            return configCache.get(workflowId);
        }
        
        try {
            File configFile = new File(configFolder, workflowId + ".json");
            if (!configFile.exists()) {
                log.warn("Workflow configuration file not found: {}", configFile.getPath());
                return null;
            }
            
            WorkflowConfigDto config = objectMapper.readValue(configFile, WorkflowConfigDto.class);
            configCache.put(workflowId, config);
            
            log.info("Loaded workflow configuration: {}", workflowId);
            return config;
            
        } catch (IOException e) {
            log.error("Failed to load workflow configuration: {}", workflowId, e);
            return null;
        }
    }
    
    public void reloadConfigurations() {
        log.info("Reloading all workflow configurations");
        configCache.clear();
        
        File folder = new File(configFolder);
        if (folder.exists() && folder.isDirectory()) {
            File[] configFiles = folder.listFiles((dir, name) -> name.endsWith(".json"));
            if (configFiles != null) {
                for (File file : configFiles) {
                    String workflowId = file.getName().replace(".json", "");
                    getWorkflowConfig(workflowId);
                }
            }
        }
    }
}