/*

package ac.workflow.service;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class HierarchicalCacheService<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(HierarchicalCacheService.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String CACHE_KEY_PREFIX = "hierarchy:";
    private static final String HIERARCHY_METADATA_KEY = "hierarchy_metadata:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(2);
    
    public HierarchicalCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    */
/**
     * Store data in hierarchical cache with broad to narrow key structure
     * @param hierarchyLevels ordered list from broad to narrow (e.g., ["country", "state", "city"])
     * @param data the data to cache
     * @param ttl time to live for the cache entry
     *//*

    public void put(List<String> hierarchyLevels, T data, Duration ttl) {
        validateHierarchyLevels(hierarchyLevels);
        
        String cacheKey = buildCacheKey(hierarchyLevels);
        
        try {
            // Store the actual data
            redisTemplate.opsForValue().set(cacheKey, data, ttl.toMillis(), TimeUnit.MILLISECONDS);
            
            // Store hierarchy metadata for efficient lookups
            storeHierarchyMetadata(hierarchyLevels, cacheKey, ttl);
            
            logger.debug("Cached data with key: {}", cacheKey);
        } catch (Exception e) {
            logger.error("Failed to cache data with key: {}", cacheKey, e);
        }
    }
    
    */
/**
     * Get data from cache using exact hierarchy match
     *//*

    @SuppressWarnings("unchecked")
    public Optional<T> get(List<String> hierarchyLevels) {
        validateHierarchyLevels(hierarchyLevels);
        
        String cacheKey = buildCacheKey(hierarchyLevels);
        
        try {
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);
            return Optional.ofNullable((T) cachedData);
        } catch (Exception e) {
            logger.error("Failed to retrieve data with key: {}", cacheKey, e);
            return Optional.empty();
        }
    }
    
    */
/**
     * Find data in cache using partial hierarchy (broad to narrow search)
     * Returns the most specific match found
     *//*

    @SuppressWarnings("unchecked")
    public Optional<T> findInHierarchy(List<String> hierarchyLevels) {
        validateHierarchyLevels(hierarchyLevels);
        
        // Search from most specific to least specific
        for (int i = hierarchyLevels.size(); i > 0; i--) {
            List<String> partialHierarchy = hierarchyLevels.subList(0, i);
            Optional<T> result = get(partialHierarchy);
            if (result.isPresent()) {
                logger.debug("Found cached data at hierarchy level: {}", partialHierarchy);
                return result;
            }
        }
        
        return Optional.empty();
    }
    
    */
/**
     * Get all cached entries that match a partial hierarchy pattern
     *//*

    @SuppressWarnings("unchecked")
    public Map<List<String>, T> getByHierarchyPattern(List<String> partialHierarchy) {
        validateHierarchyLevels(partialHierarchy);
        
        Map<List<String>, T> results = new HashMap<>();
        String pattern = buildCacheKey(partialHierarchy) + "*";
        
        try {
            Set<String> matchingKeys = redisTemplate.keys(pattern);
            if (matchingKeys != null) {
                for (String key : matchingKeys) {
                    Object cachedData = redisTemplate.opsForValue().get(key);
                    if (cachedData != null) {
                        List<String> hierarchy = parseCacheKey(key);
                        results.put(hierarchy, (T) cachedData);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve data by pattern: {}", pattern, e);
        }
        
        return results;
    }
    
    */
/**
     * Invalidate cache entries at a specific hierarchy level and all sub-levels
     *//*

    public void invalidateHierarchyLevel(List<String> hierarchyLevels) {
        validateHierarchyLevels(hierarchyLevels);
        
        String pattern = buildCacheKey(hierarchyLevels) + "*";
        
        try {
            Set<String> keysToDelete = redisTemplate.keys(pattern);
            if (keysToDelete != null && !keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                logger.info("Invalidated {} cache entries matching pattern: {}", 
                           keysToDelete.size(), pattern);
            }
            
            // Also clean up metadata
            String metadataPattern = HIERARCHY_METADATA_KEY + buildCacheKey(hierarchyLevels) + "*";
            Set<String> metadataKeys = redisTemplate.keys(metadataPattern);
            if (metadataKeys != null && !metadataKeys.isEmpty()) {
                redisTemplate.delete(metadataKeys);
            }
        } catch (Exception e) {
            logger.error("Failed to invalidate hierarchy level: {}", hierarchyLevels, e);
        }
    }
    
    */
/**
     * Get statistics about cache usage at different hierarchy levels
     *//*

    public CacheStatistics getStatistics() {
        CacheStatistics stats = new CacheStatistics();
        
        try {
            String pattern = CACHE_KEY_PREFIX + "*";
            Set<String> allKeys = redisTemplate.keys(pattern);
            
            if (allKeys != null) {
                Map<Integer, Integer> levelCounts = new HashMap<>();
                
                for (String key : allKeys) {
                    List<String> hierarchy = parseCacheKey(key);
                    int level = hierarchy.size();
                    levelCounts.put(level, levelCounts.getOrDefault(level, 0) + 1);
                }
                
                stats.setTotalEntries(allKeys.size());
                stats.setEntriesPerLevel(levelCounts);
            }
        } catch (Exception e) {
            logger.error("Failed to get cache statistics", e);
        }
        
        return stats;
    }
    
    private void validateHierarchyLevels(List<String> hierarchyLevels) {
        if (hierarchyLevels == null || hierarchyLevels.isEmpty()) {
            throw new IllegalArgumentException("Hierarchy levels cannot be null or empty");
        }
        
        for (String level : hierarchyLevels) {
            if (level == null || level.trim().isEmpty()) {
                throw new IllegalArgumentException("Hierarchy level cannot be null or empty");
            }
        }
    }
    
    private String buildCacheKey(List<String> hierarchyLevels) {
        return CACHE_KEY_PREFIX + String.join(":", hierarchyLevels);
    }
    
    private List<String> parseCacheKey(String cacheKey) {
        String keyWithoutPrefix = cacheKey.substring(CACHE_KEY_PREFIX.length());
        return Arrays.asList(keyWithoutPrefix.split(":"));
    }
    
    private void storeHierarchyMetadata(List<String> hierarchyLevels, String cacheKey, Duration ttl) {
        String metadataKey = HIERARCHY_METADATA_KEY + cacheKey;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("hierarchyLevels", hierarchyLevels);
        metadata.put("cacheKey", cacheKey);
        metadata.put("createdAt", System.currentTimeMillis());
        
        redisTemplate.opsForHash().putAll(metadataKey, metadata);
        redisTemplate.expire(metadataKey, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    @Setter
    @Getter
    public static class CacheStatistics {
        // Getters and setters
        private int totalEntries;
        private Map<Integer, Integer> entriesPerLevel = new HashMap<>();

        @Override
        public String toString() {
            return "CacheStatistics{" +
                   "totalEntries=" + totalEntries +
                   ", entriesPerLevel=" + entriesPerLevel +
                   '}';
        }
    }
}
*/
