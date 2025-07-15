package ac.workflow.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class HierarchicalCacheConfiguration {
    
    @Value("${workflow.cache.hierarchical-data-ttl:7200}")
    private long hierarchicalDataTtl;
    
    @Value("${workflow.cache.hierarchical-references-ttl:3600}")
    private long hierarchicalReferencesTtl;
    
    @Bean
    @Primary
    public CacheManager hierarchicalCacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> config = new HashMap<>();
        
        // Add hierarchical cache configurations
        config.put("hierarchicalData", new CacheConfig(hierarchicalDataTtl * 1000, 0));
        config.put("hierarchicalReferences", new CacheConfig(hierarchicalReferencesTtl * 1000, 0));
        
        // Keep existing cache configurations
        config.put("runningWorkflows", new CacheConfig(3600 * 1000, 0));
        config.put("completedWorkflows", new CacheConfig(86400 * 1000, 0));
        config.put("workflowConfigs", new CacheConfig(3600 * 1000, 0));
        
        // Add workflow-specific caches that can reuse hierarchical data
        config.put("workflowById", new CacheConfig(1800 * 1000, 0));
        config.put("workflowsByStatus", new CacheConfig(900 * 1000, 0));
        config.put("workflowsByRegion", new CacheConfig(1200 * 1000, 0));
        
        return new RedissonSpringCacheManager(redissonClient, config);
    }
}
