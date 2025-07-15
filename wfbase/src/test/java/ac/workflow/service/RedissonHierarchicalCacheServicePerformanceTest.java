package ac.workflow.service;

import ac.workflow.config.HierarchicalCacheConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for RedissonHierarchicalCacheService with real Redis server
 * 
 * Requirements:
 * - Redis server running on localhost:6379
 * - Set environment variable REDIS_PERFORMANCE_TEST=true to enable tests
 * 
 * Run with: mvn test -Dtest=*PerformanceTest -DREDIS_PERFORMANCE_TEST=true
 */
@EnabledIfEnvironmentVariable(named = "REDIS_PERFORMANCE_TEST", matches = "true")
@ActiveProfiles("performance")
class RedissonHierarchicalCacheServicePerformanceTest {
    
    private RedissonClient redissonClient;
    private CacheManager cacheManager;
    private RedissonHierarchicalCacheService<WorkflowData> cacheService;
    
    // Test data class
    static class WorkflowData {
        private String workflowId;
        private String status;
        private String region;
        private String route;
        private long executionTime;
        private Map<String, Object> metadata;
        
        public WorkflowData(String workflowId, String status, String region, String route) {
            this.workflowId = workflowId;
            this.status = status;
            this.region = region;
            this.route = route;
            this.executionTime = System.currentTimeMillis();
            this.metadata = new HashMap<>();
            this.metadata.put("size", generateLargeData(100)); // Simulate realistic data size
        }
        
        private String generateLargeData(int sizeKb) {
            StringBuilder sb = new StringBuilder();
            String chunk = "0123456789".repeat(100); // 1KB chunk
            for (int i = 0; i < sizeKb; i++) {
                sb.append(chunk);
            }
            return sb.toString();
        }
        
        // Getters and setters
        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        
        public String getRoute() { return route; }
        public void setRoute(String route) { this.route = route; }
        
        public long getExecutionTime() { return executionTime; }
        public void setExecutionTime(long executionTime) { this.executionTime = executionTime; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    @BeforeEach
    void setUp() {
        // Configure Redisson for real Redis server
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://localhost:6379")
              .setDatabase(1) // Use database 1 for tests
              .setConnectionPoolSize(50)
              .setConnectionMinimumIdleSize(10);
        
        redissonClient = Redisson.create(config);
        
        // Clear the test database before each test
        redissonClient.getKeys().flushdb();
        
        // Create cache manager
        HierarchicalCacheConfiguration cacheConfig = new HierarchicalCacheConfiguration();
        cacheManager = cacheConfig.hierarchicalCacheManager(redissonClient);
        
        cacheService = new RedissonHierarchicalCacheService<>(redissonClient, cacheManager);
    }
    
    @Test
    void testPutPerformance_SingleThread() {
        // Arrange
        int numOperations = 1000;
        List<WorkflowData> testData = generateTestData(numOperations);
        
        // Act
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numOperations; i++) {
            WorkflowData data = testData.get(i);
            List<String> hierarchy = Arrays.asList(
                "workflows", 
                data.getStatus(), 
                data.getRegion(), 
                data.getRoute(), 
                data.getWorkflowId()
            );
            cacheService.put(hierarchy, data, Duration.ofMinutes(10));
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Assert
        System.out.printf("Single-threaded PUT performance: %d operations in %d ms (%.2f ops/sec)%n", 
                         numOperations, duration, (double) numOperations / duration * 1000);
        
        assertTrue(duration < 30000, "PUT operations should complete within 30 seconds");
        
        // Verify data is stored
        RedissonHierarchicalCacheService.CacheStatistics stats = cacheService.getStatistics();
        assertEquals(numOperations, stats.getDataEntries());
        assertTrue(stats.getHierarchyReferences() >= numOperations);
    }
    
    @Test
    void testGetPerformance_SingleThread() {
        // Arrange
        int numOperations = 1000;
        List<WorkflowData> testData = generateTestData(numOperations);
        List<List<String>> hierarchies = new ArrayList<>();
        
        // Pre-populate cache
        for (int i = 0; i < numOperations; i++) {
            WorkflowData data = testData.get(i);
            List<String> hierarchy = Arrays.asList(
                "workflows", 
                data.getStatus(), 
                data.getRegion(), 
                data.getRoute(), 
                data.getWorkflowId()
            );
            hierarchies.add(hierarchy);
            cacheService.put(hierarchy, data, Duration.ofMinutes(10));
        }
        
        // Act
        long startTime = System.currentTimeMillis();
        
        for (List<String> hierarchy : hierarchies) {
            Optional<WorkflowData> result = cacheService.get(hierarchy);
            assertTrue(result.isPresent());
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Assert
        System.out.printf("Single-threaded GET performance: %d operations in %d ms (%.2f ops/sec)%n", 
                         numOperations, duration, (double) numOperations / duration * 1000);
        
        assertTrue(duration < 20000, "GET operations should complete within 20 seconds");
    }
    
    @Test
    void testConcurrentPutPerformance() throws InterruptedException {
        // Arrange
        int numThreads = 10;
        int operationsPerThread = 200;
        int totalOperations = numThreads * operationsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Future<Long>> futures = new ArrayList<>();
        
        // Act
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            Future<Long> future = executor.submit(() -> {
                try {
                    long threadStartTime = System.currentTimeMillis();
                    
                    for (int i = 0; i < operationsPerThread; i++) {
                        WorkflowData data = new WorkflowData(
                            "wf-" + threadId + "-" + i,
                            "running",
                            "us-east-" + (threadId % 3),
                            "route-" + (threadId % 5)
                        );
                        
                        List<String> hierarchy = Arrays.asList(
                            "workflows", 
                            data.getStatus(), 
                            data.getRegion(), 
                            data.getRoute(), 
                            data.getWorkflowId()
                        );
                        
                        cacheService.put(hierarchy, data, Duration.ofMinutes(10));
                    }
                    
                    return System.currentTimeMillis() - threadStartTime;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }
        
        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;
        
        // Assert
        System.out.printf("Concurrent PUT performance: %d operations in %d ms (%.2f ops/sec)%n", 
                         totalOperations, totalDuration, (double) totalOperations / totalDuration * 1000);
        
        assertTrue(totalDuration < 60000, "Concurrent PUT operations should complete within 60 seconds");
        
        // Verify all data is stored
        RedissonHierarchicalCacheService.CacheStatistics stats = cacheService.getStatistics();
        assertEquals(totalOperations, stats.getDataEntries());
        
        // Calculate per-thread performance
        for (int i = 0; i < futures.size(); i++) {
            try {
                long threadDuration = futures.get(i).get();
                System.out.printf("Thread %d: %d operations in %d ms (%.2f ops/sec)%n", 
                                 i, operationsPerThread, threadDuration, 
                                 (double) operationsPerThread / threadDuration * 1000);
            } catch (ExecutionException e) {
                fail("Thread " + i + " failed: " + e.getMessage());
            }
        }
    }
    
    @Test
    void testConcurrentMixedOperations() throws InterruptedException {
        // Arrange
        int numThreads = 8;
        int operationsPerThread = 250;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        // Pre-populate some data
        for (int i = 0; i < 100; i++) {
            WorkflowData data = new WorkflowData("pre-wf-" + i, "completed", "us-west", "route-0");
            List<String> hierarchy = Arrays.asList("workflows", "completed", "us-west", "route-0", "pre-wf-" + i);
            cacheService.put(hierarchy, data, Duration.ofMinutes(10));
        }
        
        // Act
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    Random random = new Random(threadId);
                    
                    for (int i = 0; i < operationsPerThread; i++) {
                        double operation = random.nextDouble();
                        
                        if (operation < 0.5) {
                            // 50% PUT operations
                            WorkflowData data = new WorkflowData(
                                "wf-" + threadId + "-" + i,
                                "running",
                                "us-east-" + (threadId % 3),
                                "route-" + (threadId % 5)
                            );
                            
                            List<String> hierarchy = Arrays.asList(
                                "workflows", data.getStatus(), data.getRegion(), data.getRoute(), data.getWorkflowId()
                            );
                            cacheService.put(hierarchy, data, Duration.ofMinutes(10));
                            
                        } else if (operation < 0.8) {
                            // 30% GET operations
                            List<String> hierarchy = Arrays.asList(
                                "workflows", "completed", "us-west", "route-0", "pre-wf-" + random.nextInt(100)
                            );
                            cacheService.get(hierarchy);
                            
                        } else {
                            // 20% findInHierarchy operations
                            List<String> hierarchy = Arrays.asList(
                                "workflows", "running", "us-east-" + random.nextInt(3), "route-" + random.nextInt(5), "wf-nonexistent"
                            );
                            cacheService.findInHierarchy(hierarchy);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(90, TimeUnit.SECONDS);
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        int totalOperations = numThreads * operationsPerThread;
        
        // Assert
        System.out.printf("Mixed operations performance: %d operations in %d ms (%.2f ops/sec)%n", 
                         totalOperations, duration, (double) totalOperations / duration * 1000);
        
        assertTrue(duration < 90000, "Mixed operations should complete within 90 seconds");
        
        // Print final statistics
        RedissonHierarchicalCacheService.CacheStatistics stats = cacheService.getStatistics();
        System.out.println("Final cache statistics: " + stats);
        assertTrue(stats.getDataEntries() > 100, "Should have more than initial 100 entries");
    }
    
    @Test
    void testHierarchicalQueryPerformance() {
        // Arrange
        int numRegions = 5;
        int numRoutes = 10;
        int numWorkflowsPerRoute = 50;
        
        // Populate hierarchical data
        for (int r = 0; r < numRegions; r++) {
            for (int rt = 0; rt < numRoutes; rt++) {
                for (int w = 0; w < numWorkflowsPerRoute; w++) {
                    WorkflowData data = new WorkflowData(
                        "wf-" + r + "-" + rt + "-" + w,
                        "running",
                        "region-" + r,
                        "route-" + rt
                    );
                    
                    List<String> hierarchy = Arrays.asList(
                        "workflows", "running", "region-" + r, "route-" + rt, "wf-" + r + "-" + rt + "-" + w
                    );
                    cacheService.put(hierarchy, data, Duration.ofMinutes(10));
                }
            }
        }
        
        // Act & Assert - Test different hierarchy level queries
        
        // 1. Query by region
        long startTime = System.currentTimeMillis();
        for (int r = 0; r < numRegions; r++) {
            List<String> regionHierarchy = Arrays.asList("workflows", "running", "region-" + r);
            Map<List<String>, WorkflowData> regionData = cacheService.getChildrenData(regionHierarchy);
            assertEquals(numRoutes * numWorkflowsPerRoute, regionData.size());
        }
        long regionQueryTime = System.currentTimeMillis() - startTime;
        
        // 2. Query by route
        startTime = System.currentTimeMillis();
        for (int r = 0; r < numRegions; r++) {
            for (int rt = 0; rt < numRoutes; rt++) {
                List<String> routeHierarchy = Arrays.asList("workflows", "running", "region-" + r, "route-" + rt);
                Map<List<String>, WorkflowData> routeData = cacheService.getChildrenData(routeHierarchy);
                assertEquals(numWorkflowsPerRoute, routeData.size());
            }
        }
        long routeQueryTime = System.currentTimeMillis() - startTime;
        
        // 3. Test fallback queries
        startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            List<String> nonExistentHierarchy = Arrays.asList(
                "workflows", "running", "region-0", "route-0", "non-existent-workflow"
            );
            Optional<WorkflowData> fallbackResult = cacheService.findInHierarchy(nonExistentHierarchy);
            assertTrue(fallbackResult.isPresent()); // Should find at route level
        }
        long fallbackQueryTime = System.currentTimeMillis() - startTime;
        
        // Print performance results
        System.out.printf("Hierarchical query performance:%n");
        System.out.printf("- Region queries (%d queries): %d ms (%.2f ms/query)%n", 
                         numRegions, regionQueryTime, (double) regionQueryTime / numRegions);
        System.out.printf("- Route queries (%d queries): %d ms (%.2f ms/query)%n", 
                         numRegions * numRoutes, routeQueryTime, (double) routeQueryTime / (numRegions * numRoutes));
        System.out.printf("- Fallback queries (100 queries): %d ms (%.2f ms/query)%n", 
                         fallbackQueryTime, (double) fallbackQueryTime / 100);
        
        // Performance assertions
        assertTrue(regionQueryTime < 10000, "Region queries should complete within 10 seconds");
        assertTrue(routeQueryTime < 15000, "Route queries should complete within 15 seconds");
        assertTrue(fallbackQueryTime < 5000, "Fallback queries should complete within 5 seconds");
    }
    
    @Test
    void testMemoryEfficiency() {
        // Arrange
        int numWorkflows = 1000;
        List<WorkflowData> testData = generateTestData(numWorkflows);
        
        // Store same data at multiple hierarchy levels (simulating data sharing)
        for (int i = 0; i < numWorkflows; i++) {
            WorkflowData data = testData.get(i);
            
            // Store at 3 different hierarchy levels
            List<String> fullHierarchy = Arrays.asList(
                "workflows", data.getStatus(), data.getRegion(), data.getRoute(), data.getWorkflowId()
            );
            List<String> routeHierarchy = Arrays.asList(
                "workflows", data.getStatus(), data.getRegion(), data.getRoute()
            );
            List<String> regionHierarchy = Arrays.asList(
                "workflows", data.getStatus(), data.getRegion()
            );
            
            cacheService.put(fullHierarchy, data, Duration.ofMinutes(10));
            cacheService.put(routeHierarchy, data, Duration.ofMinutes(10));
            cacheService.put(regionHierarchy, data, Duration.ofMinutes(10));
        }
        
        // Act
        RedissonHierarchicalCacheService.CacheStatistics stats = cacheService.getStatistics();
        
        // Assert
        System.out.printf("Memory efficiency test results:%n");
        System.out.printf("- Data entries: %d%n", stats.getDataEntries());
        System.out.printf("- Hierarchy references: %d%n", stats.getHierarchyReferences());
        System.out.printf("- Sharing ratio: %.2f%n", stats.getSharingRatio());
        System.out.printf("- Entries per depth: %s%n", stats.getEntriesPerDepth());
        
        // Without data sharing, we would have 3000 data entries (1000 * 3 levels)
        // With data sharing, we should have much fewer actual data entries
        assertTrue(stats.getDataEntries() < numWorkflows * 3, 
                  "Data sharing should reduce actual data entries");
        
        assertTrue(stats.getSharingRatio() > 1.0, 
                  "Sharing ratio should be greater than 1.0");
        
        // Verify data integrity
        for (int i = 0; i < Math.min(100, numWorkflows); i++) {
            WorkflowData originalData = testData.get(i);
            List<String> hierarchy = Arrays.asList(
                "workflows", originalData.getStatus(), originalData.getRegion(), 
                originalData.getRoute(), originalData.getWorkflowId()
            );
            
            Optional<WorkflowData> cachedData = cacheService.get(hierarchy);
            assertTrue(cachedData.isPresent());
            assertEquals(originalData.getWorkflowId(), cachedData.get().getWorkflowId());
        }
    }
    
    private List<WorkflowData> generateTestData(int count) {
        List<WorkflowData> data = new ArrayList<>();
        String[] statuses = {"running", "completed", "failed", "pending"};
        String[] regions = {"us-east", "us-west", "eu-central", "ap-southeast"};
        String[] routes = {"route-1", "route-2", "route-3", "route-4", "route-5"};
        
        Random random = new Random(12345); // Fixed seed for reproducibility
        
        for (int i = 0; i < count; i++) {
            WorkflowData workflow = new WorkflowData(
                "workflow-" + i,
                statuses[random.nextInt(statuses.length)],
                regions[random.nextInt(regions.length)],
                routes[random.nextInt(routes.length)]
            );
            data.add(workflow);
        }
        
        return data;
    }
}
