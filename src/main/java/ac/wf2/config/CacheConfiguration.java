package ac.wf2.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfiguration {
    
    @Value("${workflow.cache.running-workflows-ttl:3600}")
    private long runningWorkflowsTtl;
    
    @Value("${workflow.cache.completed-workflows-ttl:86400}")
    private long completedWorkflowsTtl;
    
    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> config = new HashMap<>();
        
        // Running workflows cache
        config.put("runningWorkflows", new CacheConfig(runningWorkflowsTtl * 1000, 0));
        
        // Completed workflows cache
        config.put("completedWorkflows", new CacheConfig(completedWorkflowsTtl * 1000, 0));
        
        // Workflow configurations cache
        config.put("workflowConfigs", new CacheConfig(3600 * 1000, 0));
        
        return new RedissonSpringCacheManager(redissonClient, config);
    }
}