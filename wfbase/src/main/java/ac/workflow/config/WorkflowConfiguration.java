package ac.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "workflow")
@Data
public class WorkflowConfiguration {
    private String workflowsFolder = "config/workflows";
    private RedisProperties redis = new RedisProperties();
    private Map<String, Object> cache = new HashMap<>();
    private Map<String, Object> execution = new HashMap<>();
    private Map<String, Object> scheduler = new HashMap<>();

    @Data
    public static class RedisProperties {
        private String host = "localhost";
        private int port = 6379;
        private int timeout = 3000;
        private String password;
    }

}