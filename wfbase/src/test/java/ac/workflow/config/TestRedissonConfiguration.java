package ac.workflow.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Profile("test")
public class TestRedissonConfiguration {
    
    @Bean
    @Primary
    public RedissonClient mockRedissonClient() {
        return mock(RedissonClient.class);
    }
    
    @Bean
    @Primary
    public CacheManager testCacheManager() {
        return new ConcurrentMapCacheManager("hierarchicalData", "hierarchicalReferences");
    }
}
