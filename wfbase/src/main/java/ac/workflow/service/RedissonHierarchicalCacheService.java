
package ac.workflow.service;

import org.redisson.RedissonMapCache;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedissonHierarchicalCacheService<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(RedissonHierarchicalCacheService.class);
    
    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;
    
    // Key prefixes for different data types
    private static final String DATA_PREFIX = "data:";
    private static final String HIERARCHY_PREFIX = "hierarchy:";
    private static final String REFERENCE_PREFIX = "ref:";
    private static final String METADATA_PREFIX = "meta:";
    
    // Cache names for @Cacheable integration
    private static final String DATA_CACHE_NAME = "hierarchicalData";
    private static final String HIERARCHY_CACHE_NAME = "hierarchicalReferences";
    
    public RedissonHierarchicalCacheService(RedissonClient redissonClient, CacheManager cacheManager) {
        this.redissonClient = redissonClient;
        this.cacheManager = cacheManager;
    }
    
    /**
     * Store data with hierarchical references using Redisson
     */
    public void put(List<String> hierarchyLevels, T data, Duration ttl) {
        validateHierarchyLevels(hierarchyLevels);
        
        String dataId = generateDataId();
        String hierarchyKey = buildHierarchyKey(hierarchyLevels);
        
        try {
            // Store the actual data in a Redisson Map with TTL
/*
            RMap<String, T> dataMap = redissonClient.getMap(DATA_CACHE_NAME);
            dataMap.put(dataId, data, ttl.toMillis(), TimeUnit.MILLISECONDS);
*/
            RMap<String, T> dataMap = (RedissonMapCache<String, T>) cacheManager.getCache(DATA_CACHE_NAME).getNativeCache();
            dataMap.put(dataId, data);


            
            // Store hierarchy reference in a separate cache
            HierarchyReference reference = new HierarchyReference(dataId, hierarchyLevels, System.currentTimeMillis());
            RMap<String, HierarchyReference> hierarchyMap = (RedissonMapCache<String, HierarchyReference>) cacheManager.getCache(HIERARCHY_CACHE_NAME).getNativeCache();
            hierarchyMap.put(hierarchyKey, reference);
            
            // Store metadata for efficient lookups
            storeHierarchyMetadata(hierarchyLevels, dataId, hierarchyKey, ttl);
            
            // Update parent-child relationships
            updateParentReferences(hierarchyLevels, dataId, hierarchyKey, ttl);
            
            // Also store in Spring Cache for @Cacheable integration
            Cache dataCache = cacheManager.getCache(DATA_CACHE_NAME);
            if (dataCache != null) {
                dataCache.put(dataId, data);
            }
            
            logger.debug("Cached data with ID: {} at hierarchy: {}", dataId, hierarchyLevels);
            
        } catch (Exception e) {
            logger.error("Failed to cache data at hierarchy: {}", hierarchyLevels, e);
        }
    }
    
    /**
     * Get data using the exact hierarchy match
     */
    @SuppressWarnings("unchecked")
    public Optional<T> get(List<String> hierarchyLevels) {
        validateHierarchyLevels(hierarchyLevels);
        
        String hierarchyKey = buildHierarchyKey(hierarchyLevels);
        
        try {
            RMap<String, HierarchyReference> hierarchyMap = redissonClient.getMap(HIERARCHY_CACHE_NAME);
            HierarchyReference reference = hierarchyMap.get(hierarchyKey);
            
            if (reference != null) {
                return getDataById(reference.getDataId());
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve data for hierarchy: {}", hierarchyLevels, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Get data by ID - used for @Cacheable integration
     */
    @SuppressWarnings("unchecked")
    public Optional<T> getDataById(String dataId) {
        try {
            // Try Spring Cache first (for @Cacheable integration)
            Cache dataCache = cacheManager.getCache(DATA_CACHE_NAME);
            if (dataCache != null) {
                Cache.ValueWrapper wrapper = dataCache.get(dataId);
                if (wrapper != null) {
                    return Optional.ofNullable((T) wrapper.get());
                }
            }
            
            // Fallback to direct Redisson access
            RMap<String, T> dataMap = redissonClient.getMap(DATA_CACHE_NAME);
            T data = dataMap.get(dataId);
            
            // Update Spring Cache for future @Cacheable calls
            if (data != null && dataCache != null) {
                dataCache.put(dataId, data);
            }
            
            return Optional.ofNullable(data);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve data by ID: {}", dataId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Find data with fallback to broader hierarchy levels
     */
    public Optional<T> findInHierarchy(List<String> hierarchyLevels) {
        validateHierarchyLevels(hierarchyLevels);
        
        for (int i = hierarchyLevels.size(); i > 0; i--) {
            List<String> partialHierarchy = hierarchyLevels.subList(0, i);
            Optional<T> result = get(partialHierarchy);
            if (result.isPresent()) {
                logger.debug("Found data at hierarchy level: {}", partialHierarchy);
                return result;
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Get all data entries that are children of the specified hierarchy level
     */
    public Map<List<String>, T> getChildrenData(List<String> parentHierarchy) {
        validateHierarchyLevels(parentHierarchy);
        
        Map<List<String>, T> results = new HashMap<>();
        
        try {
            Set<String> childrenKeys = getChildrenKeys(parentHierarchy);
            RMap<String, HierarchyReference> hierarchyMap = redissonClient.getMap(HIERARCHY_CACHE_NAME);
            
            for (String childKey : childrenKeys) {
                HierarchyReference reference = hierarchyMap.get(childKey);
                if (reference != null) {
                    Optional<T> data = getDataById(reference.getDataId());
                    data.ifPresent(value -> results.put(reference.getHierarchyLevels(), value));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve children data for hierarchy: {}", parentHierarchy, e);
        }
        
        return results;
    }
    
    /**
     * Get aggregated data from multiple hierarchy levels
     */
    public HierarchyDataContainer<T> getAggregatedData(List<String> hierarchyLevels) {
        validateHierarchyLevels(hierarchyLevels);

        HierarchyDataContainer<T> container = new HierarchyDataContainer<>();

        try {
            // Get direct data
            Optional<T> directData = get(hierarchyLevels);
            directData.ifPresent(data -> container.setDirectData(hierarchyLevels, data));

            // Get children data
            Map<List<String>, T> childrenData = getChildrenData(hierarchyLevels);
            if (childrenData != null && !childrenData.isEmpty()) {
                container.setChildrenData(childrenData);
            }

            // Get parent data for context
            if (hierarchyLevels.size() > 1) {
                // Create a defensive copy to avoid memory leaks from the subList view
                List<String> parentHierarchy = new ArrayList<>(
                        hierarchyLevels.subList(0, hierarchyLevels.size() - 1)
                );
                Optional<T> parentData = get(parentHierarchy);
                if (parentData.isPresent()) {
                    // Store parent data with hierarchy context for consistency
                    Map<List<String>, T> parentMap = new HashMap<>();
                    parentMap.put(parentHierarchy, parentData.get());
                    container.setParentData(parentMap);
                }
            }

            logger.debug("Successfully aggregated data for hierarchy: {}", hierarchyLevels);

        } catch (Exception e) {
            logger.error("Failed to retrieve aggregated data for hierarchy: {}", hierarchyLevels, e);
            // Return partial data that was successfully retrieved rather than throwing
            // This allows the application to continue with whatever data is available
        }

        return container;
    }
    
    /**
     * Get hierarchy reference for a given hierarchy - useful for getting dataId
     */
    public Optional<HierarchyReference> getHierarchyReference(List<String> hierarchyLevels) {
        validateHierarchyLevels(hierarchyLevels);
        
        String hierarchyKey = buildHierarchyKey(hierarchyLevels);
        
        try {
            RMap<String, HierarchyReference> hierarchyMap = redissonClient.getMap(HIERARCHY_CACHE_NAME);
            HierarchyReference reference = hierarchyMap.get(hierarchyKey);
            return Optional.ofNullable(reference);
        } catch (Exception e) {
            logger.error("Failed to retrieve hierarchy reference for: {}", hierarchyLevels, e);
            return Optional.empty();
        }
    }
    
    /**
     * Invalidate hierarchy level and optionally its children
     */
    public void invalidateHierarchyLevel(List<String> hierarchyLevels, boolean includeChildren) {
        validateHierarchyLevels(hierarchyLevels);
        
        try {
            Set<String> dataIdsToDelete = new HashSet<>();
            Set<String> hierarchyKeysToDelete = new HashSet<>();
            
            String hierarchyKey = buildHierarchyKey(hierarchyLevels);
            RMap<String, HierarchyReference> hierarchyMap = redissonClient.getMap(HIERARCHY_CACHE_NAME);
            
            HierarchyReference reference = hierarchyMap.get(hierarchyKey);
            if (reference != null) {
                dataIdsToDelete.add(reference.getDataId());
                hierarchyKeysToDelete.add(hierarchyKey);
                
                if (includeChildren) {
                    Set<String> childrenKeys = getChildrenKeys(hierarchyLevels);
                    for (String childKey : childrenKeys) {
                        HierarchyReference childRef = hierarchyMap.get(childKey);
                        if (childRef != null) {
                            dataIdsToDelete.add(childRef.getDataId());
                            hierarchyKeysToDelete.add(childKey);
                        }
                    }
                }
            }
            
            // Delete from Redisson maps
            RMap<String, T> dataMap = redissonClient.getMap(DATA_CACHE_NAME);
            for (String dataId : dataIdsToDelete) {
                dataMap.remove(dataId);
            }
            
            for (String hierarchyKey2 : hierarchyKeysToDelete) {
                hierarchyMap.remove(hierarchyKey2);
            }
            
            // Also invalidate from Spring Cache
            Cache dataCache = cacheManager.getCache(DATA_CACHE_NAME);
            if (dataCache != null) {
                for (String dataId : dataIdsToDelete) {
                    dataCache.evict(dataId);
                }
            }
            
            // Clean up metadata and references
            cleanupMetadata(hierarchyLevels, includeChildren);
            
            logger.info("Invalidated hierarchy: {}, children: {}, total entries: {}", 
                       hierarchyLevels, includeChildren, dataIdsToDelete.size());
            
        } catch (Exception e) {
            logger.error("Failed to invalidate hierarchy level: {}", hierarchyLevels, e);
        }
    }
    
    /**
     * Get statistics about cache usage
     */
    public CacheStatistics getStatistics() {
        CacheStatistics stats = new CacheStatistics();
        
        try {
            RMap<String, HierarchyReference> hierarchyMap = redissonClient.getMap(HIERARCHY_CACHE_NAME);
            RMap<String, T> dataMap = redissonClient.getMap(DATA_CACHE_NAME);
            
            stats.setHierarchyReferences(hierarchyMap.size());
            stats.setDataEntries(dataMap.size());
            
            if (stats.getHierarchyReferences() > 0 && stats.getDataEntries() > 0) {
                double sharingRatio = (double) stats.getHierarchyReferences() / stats.getDataEntries();
                stats.setSharingRatio(sharingRatio);
            }
            
            // Count by hierarchy depth
            Map<Integer, Integer> depthCounts = new HashMap<>();
            for (HierarchyReference ref : hierarchyMap.values()) {
                int depth = ref.getHierarchyLevels().size();
                depthCounts.put(depth, depthCounts.getOrDefault(depth, 0) + 1);
            }
            stats.setEntriesPerDepth(depthCounts);
            
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
    
    private String buildHierarchyKey(List<String> hierarchyLevels) {
        return HIERARCHY_PREFIX + String.join(":", hierarchyLevels);
    }
    
    private String generateDataId() {
        return UUID.randomUUID().toString();
    }
    
    private void storeHierarchyMetadata(List<String> hierarchyLevels, String dataId, String hierarchyKey, Duration ttl) {
        try {
            String metadataKey = METADATA_PREFIX + buildHierarchyKey(hierarchyLevels);
            RMap<String, Object> metadataMap = redissonClient.getMap(metadataKey);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("dataId", dataId);
            metadata.put("hierarchyKey", hierarchyKey);
            metadata.put("hierarchyLevels", hierarchyLevels);
            metadata.put("createdAt", System.currentTimeMillis());
            metadata.put("depth", hierarchyLevels.size());
            
            metadataMap.putAll(metadata);
            metadataMap.expire(ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warn("Failed to store hierarchy metadata", e);
        }
    }
    
    private void updateParentReferences(List<String> hierarchyLevels, String dataId, String hierarchyKey, Duration ttl) {
        try {
            for (int i = 1; i < hierarchyLevels.size(); i++) {
                List<String> parentHierarchy = hierarchyLevels.subList(0, i);
                String parentChildrenKey = REFERENCE_PREFIX + "children:" + buildHierarchyKey(parentHierarchy);
                
                RSet<String> childrenSet = redissonClient.getSet(parentChildrenKey);
                childrenSet.add(hierarchyKey);
                childrenSet.expire(ttl.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            logger.warn("Failed to update parent references", e);
        }
    }
    
    private Set<String> getChildrenKeys(List<String> parentHierarchy) {
        try {
            String parentChildrenKey = REFERENCE_PREFIX + "children:" + buildHierarchyKey(parentHierarchy);
            RSet<String> childrenSet = redissonClient.getSet(parentChildrenKey);
            return new HashSet<>(childrenSet);
        } catch (Exception e) {
            logger.warn("Failed to get children keys for hierarchy: {}", parentHierarchy, e);
            return new HashSet<>();
        }
    }
    
    private void cleanupMetadata(List<String> hierarchyLevels, boolean includeChildren) {
        try {
            String metadataKey = METADATA_PREFIX + buildHierarchyKey(hierarchyLevels);
            redissonClient.getMap(metadataKey).delete();
            
            if (includeChildren) {
                String parentChildrenKey = REFERENCE_PREFIX + "children:" + buildHierarchyKey(hierarchyLevels);
                redissonClient.getSet(parentChildrenKey).delete();
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup metadata", e);
        }
    }
    
    // Inner classes remain the same
    public static class HierarchyReference {
        private String dataId;
        private List<String> hierarchyLevels;
        private long createdAt;
        
        public HierarchyReference() {}
        
        public HierarchyReference(String dataId, List<String> hierarchyLevels, long createdAt) {
            this.dataId = dataId;
            this.hierarchyLevels = hierarchyLevels;
            this.createdAt = createdAt;
        }
        
        public String getDataId() { return dataId; }
        public void setDataId(String dataId) { this.dataId = dataId; }
        
        public List<String> getHierarchyLevels() { return hierarchyLevels; }
        public void setHierarchyLevels(List<String> hierarchyLevels) { this.hierarchyLevels = hierarchyLevels; }
        
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }
    
    public static class HierarchyDataContainer<T> {
        private Map<List<String>, T> directData = new HashMap<>();
        private Map<List<String>, T> childrenData = new HashMap<>();
        private Map<List<String>, T> parentData = new HashMap<>();
        
        public void setDirectData(List<String> hierarchy, T data) {
            directData.put(hierarchy, data);
        }
        
        public Map<List<String>, T> getDirectData() { return directData; }
        public void setDirectData(Map<List<String>, T> directData) { this.directData = directData; }
        
        public Map<List<String>, T> getChildrenData() { return childrenData; }
        public void setChildrenData(Map<List<String>, T> childrenData) { this.childrenData = childrenData; }
        
        public Map<List<String>, T> getParentData() { return parentData; }
        public void setParentData(Map<List<String>, T> parentData) { this.parentData = parentData; }
    }
    
    public static class CacheStatistics {
        private int hierarchyReferences;
        private int dataEntries;
        private double sharingRatio;
        private Map<Integer, Integer> entriesPerDepth = new HashMap<>();
        
        public int getHierarchyReferences() { return hierarchyReferences; }
        public void setHierarchyReferences(int hierarchyReferences) { this.hierarchyReferences = hierarchyReferences; }
        
        public int getDataEntries() { return dataEntries; }
        public void setDataEntries(int dataEntries) { this.dataEntries = dataEntries; }
        
        public double getSharingRatio() { return sharingRatio; }
        public void setSharingRatio(double sharingRatio) { this.sharingRatio = sharingRatio; }
        
        public Map<Integer, Integer> getEntriesPerDepth() { return entriesPerDepth; }
        public void setEntriesPerDepth(Map<Integer, Integer> entriesPerDepth) { this.entriesPerDepth = entriesPerDepth; }
        
        @Override
        public String toString() {
            return "CacheStatistics{" +
                   "hierarchyReferences=" + hierarchyReferences +
                   ", dataEntries=" + dataEntries +
                   ", sharingRatio=" + String.format("%.2f", sharingRatio) +
                   ", entriesPerDepth=" + entriesPerDepth +
                   '}';
        }
    }
}
