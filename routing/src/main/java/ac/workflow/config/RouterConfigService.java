package ac.workflow.config;

import ac.workflow.domain.RouteConfig;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RouterConfigService {
    private final Map<String, RouteConfig> routeConfigs = new HashMap<>();
    
    public RouterConfigService() {
        loadConfiguration();
    }
    
    private void loadConfiguration() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = getClass().getResourceAsStream("/router-service.yml");
            Map<String, Object> config = yaml.load(inputStream);
            
            List<Map<String, Object>> routes = (List<Map<String, Object>>) config.get("routes");
            for (Map<String, Object> routeData : routes) {
                RouteConfig routeConfig = new RouteConfig();
                routeConfig.setRouteId((String) routeData.get("routeId"));
                routeConfig.setEndpointType((String) routeData.get("endpointType"));
                routeConfig.setEndpointUrl((String) routeData.get("endpointUrl"));
                routeConfig.setUserId((String) routeData.get("userId"));
                routeConfig.setPassword((String) routeData.get("password"));
                routeConfig.setProperties((Map<String, Object>) routeData.get("properties"));
                routeConfig.setStatusThresholdMinutes((Integer) routeData.getOrDefault("statusThresholdMinutes", 5));
                
                routeConfigs.put(routeConfig.getRouteId(), routeConfig);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load router configuration", e);
        }
    }
    
    public RouteConfig getRouteConfig(String routeId) {
        return routeConfigs.get(routeId);
    }
    
    public boolean hasRoute(String routeId) {
        return routeConfigs.containsKey(routeId);
    }
}
