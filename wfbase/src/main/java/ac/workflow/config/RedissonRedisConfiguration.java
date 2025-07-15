package ac.workflow.config;

import lombok.extern.log4j.Log4j2;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;

@Log4j2
@Configuration
@EnableCaching
@EnableTransactionManagement
public class RedissonRedisConfiguration {

    @Value("${workflow.cache.running-workflows-ttl:3600}")
    private long runningWorkflowsTtl;

    @Value("${workflow.cache.completed-workflows-ttl:86400}")
    private long completedWorkflowsTtl;

    @Bean
    public RedissonClient redissonClient(WorkflowConfiguration workflowConfig) throws IOException {
        Config config = new Config();

        String redisAddress = "redis://" + workflowConfig.getRedis().getHost() + ":" + workflowConfig.getRedis().getPort();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig
                .setAddress(redisAddress)
                .setConnectTimeout(workflowConfig.getRedis().getTimeout())
                .setTimeout(workflowConfig.getRedis().getTimeout());

        if (workflowConfig.getRedis().getPassword() != null && !workflowConfig.getRedis().getPassword().isEmpty()) {
            singleServerConfig.setPassword(workflowConfig.getRedis().getPassword());
        }

        return Redisson.create(config);
    }

    @Bean
    @Primary
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedissonConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setEnableTransactionSupport(true);
        return template;
    }

/*
    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient, WorkflowConfiguration workflowConfig) {
        Map<String, org.redisson.spring.cache.CacheConfig> config = new HashMap<>();

        // Configure different TTLs for different caches

        long workflowsTtl = Long.parseLong(
                String.valueOf(workflowConfig.getCache().getOrDefault("workflows-ttl", 86400)));
        config.put("workflows", new org.redisson.spring.cache.CacheConfig(workflowsTtl, 0));

        long executionTtl = Long.parseLong(
                String.valueOf(workflowConfig.getCache().getOrDefault("execution-ttl", 1800)));

        config.put("workflowExecutions", new org.redisson.spring.cache.CacheConfig(executionTtl, 0));

        // Running workflows cache
        config.put("runningWorkflows", new CacheConfig(runningWorkflowsTtl * 1000, 0));

        // Completed workflows cache
        config.put("completedWorkflows", new CacheConfig(completedWorkflowsTtl * 1000, 0));

        // Workflow configurations cache
        config.put("workflowConfigs", new CacheConfig(3600 * 1000, 0));

        return new RedissonSpringCacheManager(redissonClient, config);
    }
*/

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                            MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic("chat"));

        return container;
    }
}