
package ac.workflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedissonHierarchicalCacheServiceTest {

    @Mock
    private RedissonClient redissonClient;
    
    @Mock
    private CacheManager cacheManager;
    
    @Mock
    private Cache springCache;
    
    @Mock
    private Cache.ValueWrapper valueWrapper;
    
    @Mock
    private RMap<String, TestData> dataMap;
    
    @Mock
    private RMap<String, RedissonHierarchicalCacheService.HierarchyReference> hierarchyMap;
    
    @Mock
    private RSet<String> childrenSet;
    
    @Mock
    private RMap<String, Object> metadataMap;
    
    private RedissonHierarchicalCacheService<TestData> cacheService;
    
    // Test data class
    static class TestData {
        private String id;
        private String value;
        private long timestamp;
        
        public TestData() {}
        
        public TestData(String id, String value) {
            this.id = id;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestData testData = (TestData) o;
            return Objects.equals(id, testData.id) && Objects.equals(value, testData.value);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(id, value);
        }
    }
    
    @BeforeEach
    void setUp() {
        cacheService = new RedissonHierarchicalCacheService<>(redissonClient, cacheManager);
        
        // Setup mock behaviors
        when(redissonClient.getMap("hierarchicalData")).thenReturn(dataMap);
        when(redissonClient.getMap("hierarchicalReferences")).thenReturn(hierarchyMap);
        when(redissonClient.getSet(anyString())).thenReturn(childrenSet);
        when(redissonClient.getMap(startsWith("meta:"))).thenReturn(metadataMap);
        
        when(cacheManager.getCache("hierarchicalData")).thenReturn(springCache);
        when(cacheManager.getCache("hierarchicalReferences")).thenReturn(springCache);
    }
    
    @Test
    void testPutAndGet_Success() {
        // Arrange
        List<String> hierarchy = Arrays.asList("level1", "level2", "level3");
        TestData testData = new TestData("test-id", "test-value");
        Duration ttl = Duration.ofMinutes(10);
        
        RedissonHierarchicalCacheService.HierarchyReference expectedReference = 
            new RedissonHierarchicalCacheService.HierarchyReference("data-id", hierarchy, System.currentTimeMillis());
        
        when(hierarchyMap.get("hierarchy:level1:level2:level3")).thenReturn(expectedReference);
        when(dataMap.get("data-id")).thenReturn(testData);
        when(springCache.get("data-id")).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testData);
        
        // Act
        cacheService.put(hierarchy, testData, ttl);
        Optional<TestData> result = cacheService.get(hierarchy);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(testData, result.get());
        
        verify(dataMap).put(anyString(), eq(testData), eq(ttl.toMillis()), eq(TimeUnit.MILLISECONDS));
        verify(hierarchyMap).put(eq("hierarchy:level1:level2:level3"), any(RedissonHierarchicalCacheService.HierarchyReference.class), eq(ttl.toMillis()), eq(TimeUnit.MILLISECONDS));
        verify(springCache).put(anyString(), eq(testData));
    }
    
    @Test
    void testGet_NotFound() {
        // Arrange
        List<String> hierarchy = Arrays.asList("nonexistent", "level");
        when(hierarchyMap.get("hierarchy:nonexistent:level")).thenReturn(null);
        
        // Act
        Optional<TestData> result = cacheService.get(hierarchy);
        
        // Assert
        assertFalse(result.isPresent());
        verify(hierarchyMap).get("hierarchy:nonexistent:level");
    }
    
    @Test
    void testFindInHierarchy_FallbackToParent() {
        // Arrange
        List<String> hierarchy = Arrays.asList("level1", "level2", "level3");
        TestData testData = new TestData("test-id", "test-value");
        
        // Mock: level3 not found, level2 not found, level1 found
        when(hierarchyMap.get("hierarchy:level1:level2:level3")).thenReturn(null);
        when(hierarchyMap.get("hierarchy:level1:level2")).thenReturn(null);
        
        RedissonHierarchicalCacheService.HierarchyReference level1Reference = 
            new RedissonHierarchicalCacheService.HierarchyReference("data-id", Arrays.asList("level1"), System.currentTimeMillis());
        when(hierarchyMap.get("hierarchy:level1")).thenReturn(level1Reference);
        when(springCache.get("data-id")).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testData);
        
        // Act
        Optional<TestData> result = cacheService.findInHierarchy(hierarchy);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(testData, result.get());
        
        // Verify that it tried all levels
        verify(hierarchyMap).get("hierarchy:level1:level2:level3");
        verify(hierarchyMap).get("hierarchy:level1:level2");
        verify(hierarchyMap).get("hierarchy:level1");
    }
    
    @Test
    void testGetDataById_SpringCacheFirst() {
        // Arrange
        String dataId = "test-data-id";
        TestData testData = new TestData("test-id", "test-value");
        
        when(springCache.get(dataId)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testData);
        
        // Act
        Optional<TestData> result = cacheService.getDataById(dataId);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(testData, result.get());
        
        verify(springCache).get(dataId);
        verify(dataMap, never()).get(dataId); // Should not fallback to Redisson
    }
    
    @Test
    void testGetDataById_FallbackToRedisson() {
        // Arrange
        String dataId = "test-data-id";
        TestData testData = new TestData("test-id", "test-value");
        
        when(springCache.get(dataId)).thenReturn(null);
        when(dataMap.get(dataId)).thenReturn(testData);
        
        // Act
        Optional<TestData> result = cacheService.getDataById(dataId);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(testData, result.get());
        
        verify(springCache).get(dataId);
        verify(dataMap).get(dataId);
        verify(springCache).put(dataId, testData); // Should update Spring cache
    }
    
    @Test
    void testGetChildrenData() {
        // Arrange
        List<String> parentHierarchy = Arrays.asList("parent", "level");
        TestData child1 = new TestData("child1", "value1");
        TestData child2 = new TestData("child2", "value2");
        
        Set<String> childrenKeys = Set.of("hierarchy:parent:level:child1", "hierarchy:parent:level:child2");
        when(childrenSet.iterator()).thenReturn(childrenKeys.iterator());
        when(childrenSet.size()).thenReturn(childrenKeys.size());
        
        RedissonHierarchicalCacheService.HierarchyReference child1Ref = 
            new RedissonHierarchicalCacheService.HierarchyReference("child1-id", Arrays.asList("parent", "level", "child1"), System.currentTimeMillis());
        RedissonHierarchicalCacheService.HierarchyReference child2Ref = 
            new RedissonHierarchicalCacheService.HierarchyReference("child2-id", Arrays.asList("parent", "level", "child2"), System.currentTimeMillis());
        
        when(hierarchyMap.get("hierarchy:parent:level:child1")).thenReturn(child1Ref);
        when(hierarchyMap.get("hierarchy:parent:level:child2")).thenReturn(child2Ref);
        
        when(springCache.get("child1-id")).thenReturn(mock(Cache.ValueWrapper.class));
        when(springCache.get("child2-id")).thenReturn(mock(Cache.ValueWrapper.class));
        when(springCache.get("child1-id").get()).thenReturn(child1);
        when(springCache.get("child2-id").get()).thenReturn(child2);
        
        // Act
        Map<List<String>, TestData> result = cacheService.getChildrenData(parentHierarchy);
        
        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsValue(child1));
        assertTrue(result.containsValue(child2));
    }
    
    @Test
    void testGetAggregatedData() {
        // Arrange
        List<String> hierarchy = Arrays.asList("level1", "level2");
        TestData directData = new TestData("direct", "direct-value");
        TestData parentData = new TestData("parent", "parent-value");
        TestData childData = new TestData("child", "child-value");
        
        // Mock direct data
        RedissonHierarchicalCacheService.HierarchyReference directRef = 
            new RedissonHierarchicalCacheService.HierarchyReference("direct-id", hierarchy, System.currentTimeMillis());
        when(hierarchyMap.get("hierarchy:level1:level2")).thenReturn(directRef);
        when(springCache.get("direct-id")).thenReturn(mock(Cache.ValueWrapper.class));
        when(springCache.get("direct-id").get()).thenReturn(directData);
        
        // Mock parent data
        RedissonHierarchicalCacheService.HierarchyReference parentRef = 
            new RedissonHierarchicalCacheService.HierarchyReference("parent-id", Arrays.asList("level1"), System.currentTimeMillis());
        when(hierarchyMap.get("hierarchy:level1")).thenReturn(parentRef);
        when(springCache.get("parent-id")).thenReturn(mock(Cache.ValueWrapper.class));
        when(springCache.get("parent-id").get()).thenReturn(parentData);
        
        // Mock children data
        Set<String> childrenKeys = Set.of("hierarchy:level1:level2:child");
        when(childrenSet.iterator()).thenReturn(childrenKeys.iterator());
        when(childrenSet.size()).thenReturn(childrenKeys.size());
        
        RedissonHierarchicalCacheService.HierarchyReference childRef = 
            new RedissonHierarchicalCacheService.HierarchyReference("child-id", Arrays.asList("level1", "level2", "child"), System.currentTimeMillis());
        when(hierarchyMap.get("hierarchy:level1:level2:child")).thenReturn(childRef);
        when(springCache.get("child-id")).thenReturn(mock(Cache.ValueWrapper.class));
        when(springCache.get("child-id").get()).thenReturn(childData);
        
        // Act
        RedissonHierarchicalCacheService.HierarchyDataContainer<TestData> result = cacheService.getAggregatedData(hierarchy);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.getDirectData().size());
        assertEquals(1, result.getParentData().size());
        assertEquals(1, result.getChildrenData().size());
        
        assertTrue(result.getDirectData().containsValue(directData));
        assertTrue(result.getParentData().containsValue(parentData));
        assertTrue(result.getChildrenData().containsValue(childData));
    }
    
    @Test
    void testInvalidateHierarchyLevel_WithChildren() {
        // Arrange
        List<String> hierarchy = Arrays.asList("level1", "level2");
        
        RedissonHierarchicalCacheService.HierarchyReference parentRef = 
            new RedissonHierarchicalCacheService.HierarchyReference("parent-id", hierarchy, System.currentTimeMillis());
        when(hierarchyMap.get("hierarchy:level1:level2")).thenReturn(parentRef);
        
        Set<String> childrenKeys = Set.of("hierarchy:level1:level2:child");
        when(childrenSet.iterator()).thenReturn(childrenKeys.iterator());
        when(childrenSet.size()).thenReturn(childrenKeys.size());
        
        RedissonHierarchicalCacheService.HierarchyReference childRef = 
            new RedissonHierarchicalCacheService.HierarchyReference("child-id", Arrays.asList("level1", "level2", "child"), System.currentTimeMillis());
        when(hierarchyMap.get("hierarchy:level1:level2:child")).thenReturn(childRef);
        
        // Act
        cacheService.invalidateHierarchyLevel(hierarchy, true);
        
        // Assert
        verify(dataMap).remove("parent-id");
        verify(dataMap).remove("child-id");
        verify(hierarchyMap).remove("hierarchy:level1:level2");
        verify(hierarchyMap).remove("hierarchy:level1:level2:child");
        verify(springCache).evict("parent-id");
        verify(springCache).evict("child-id");
    }
    
    @Test
    void testGetStatistics() {
        // Arrange
        when(hierarchyMap.size()).thenReturn(10);
        when(dataMap.size()).thenReturn(5);
        
        List<RedissonHierarchicalCacheService.HierarchyReference> references = Arrays.asList(
            new RedissonHierarchicalCacheService.HierarchyReference("id1", Arrays.asList("l1"), System.currentTimeMillis()),
            new RedissonHierarchicalCacheService.HierarchyReference("id2", Arrays.asList("l1", "l2"), System.currentTimeMillis()),
            new RedissonHierarchicalCacheService.HierarchyReference("id3", Arrays.asList("l1", "l2", "l3"), System.currentTimeMillis())
        );
        when(hierarchyMap.values()).thenReturn(references);
        
        // Act
        RedissonHierarchicalCacheService.CacheStatistics stats = cacheService.getStatistics();
        
        // Assert
        assertEquals(10, stats.getHierarchyReferences());
        assertEquals(5, stats.getDataEntries());
        assertEquals(2.0, stats.getSharingRatio(), 0.01);
        
        Map<Integer, Integer> expectedDepthCounts = Map.of(1, 1, 2, 1, 3, 1);
        assertEquals(expectedDepthCounts, stats.getEntriesPerDepth());
    }
    
    @Test
    void testValidateHierarchyLevels_ThrowsException() {
        // Test null hierarchy
        assertThrows(IllegalArgumentException.class, () -> 
            cacheService.put(null, new TestData("id", "value"), Duration.ofMinutes(5))
        );
        
        // Test empty hierarchy
        assertThrows(IllegalArgumentException.class, () -> 
            cacheService.put(Arrays.asList(), new TestData("id", "value"), Duration.ofMinutes(5))
        );
        
        // Test null level in hierarchy
        assertThrows(IllegalArgumentException.class, () -> 
            cacheService.put(Arrays.asList("level1", null, "level3"), new TestData("id", "value"), Duration.ofMinutes(5))
        );
        
        // Test empty level in hierarchy
        assertThrows(IllegalArgumentException.class, () -> 
            cacheService.put(Arrays.asList("level1", "", "level3"), new TestData("id", "value"), Duration.ofMinutes(5))
        );
    }
    
    @Test
    void testGetHierarchyReference() {
        // Arrange
        List<String> hierarchy = Arrays.asList("level1", "level2");
        RedissonHierarchicalCacheService.HierarchyReference expectedRef = 
            new RedissonHierarchicalCacheService.HierarchyReference("data-id", hierarchy, System.currentTimeMillis());
        
        when(hierarchyMap.get("hierarchy:level1:level2")).thenReturn(expectedRef);
        
        // Act
        Optional<RedissonHierarchicalCacheService.HierarchyReference> result = cacheService.getHierarchyReference(hierarchy);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedRef, result.get());
        verify(hierarchyMap).get("hierarchy:level1:level2");
    }
    
    @Test
    void testExceptionHandling() {
        // Arrange
        List<String> hierarchy = Arrays.asList("level1", "level2");
        when(hierarchyMap.get(anyString())).thenThrow(new RuntimeException("Redis connection error"));
        
        // Act & Assert - should not throw exception but return empty
        Optional<TestData> result = cacheService.get(hierarchy);
        assertFalse(result.isPresent());
        
        Optional<TestData> fallbackResult = cacheService.findInHierarchy(hierarchy);
        assertFalse(fallbackResult.isPresent());
    }
}
