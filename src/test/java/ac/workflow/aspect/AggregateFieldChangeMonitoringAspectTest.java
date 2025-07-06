package ac.workflow.aspect;

import ac.workflow.aspect.annotation.TrackFieldChanges;
import ac.workflow.aspect.dto.AggregateChangeMetadata;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.custom.OptimizedAggregateUpdateRepository;
import ac.workflow.service.monitoring.AggregateChangeDetectorService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AggregateFieldChangeMonitoringAspectTest {

    @Mock
    private AggregateChangeDetectorService aggregateChangeDetectorService;

    @Mock
    private OptimizedAggregateUpdateRepository optimizedAggregateUpdateRepository;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    @InjectMocks
    private AggregateFieldChangeMonitoringAspect aspect;

    private TrackFieldChanges trackFieldChanges;
    private Workflow testWorkflow;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger aspectLogger;

    @BeforeEach
    void setUp() {
        trackFieldChanges = createTrackFieldChangesAnnotation();
        testWorkflow = createTestWorkflow();

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");

        // Set up logging capture for verification
        setupLogCapture();
    }

    private void setupLogCapture() {
        aspectLogger = (Logger) LoggerFactory.getLogger(AggregateFieldChangeMonitoringAspect.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        aspectLogger.addAppender(logAppender);
    }

    // Basic functionality tests

    @Test
    void monitorAggregateChanges_ShouldSkip_WhenNotAggregateRoot() throws Throwable {
        // Given
        TrackFieldChanges nonAggregateAnnotation = createTrackFieldChangesAnnotation(false, true, 1000L, new String[0], 10);
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, nonAggregateAnnotation, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService, never()).captureAggregateSnapshot(any());
        verify(joinPoint).proceed();
        verifyLogMessage("Method not configured for aggregate root monitoring, skipping", Level.DEBUG);
    }

    @Test
    void monitorAggregateChanges_ShouldSkip_WhenAggregateIsNull() throws Throwable {
        // Given
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, null);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService, never()).captureAggregateSnapshot(any());
        verify(joinPoint).proceed();
        verifyLogMessage("Aggregate argument is null, skipping aggregate change monitoring", Level.DEBUG);
    }

    @Test
    void monitorAggregateChanges_ShouldSkip_WhenAggregateIdIsNull() throws Throwable {
        // Given
        Workflow workflowWithNullId = createTestWorkflow("workflow-without-id", null);
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, workflowWithNullId);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService, never()).captureAggregateSnapshot(any());
        verify(joinPoint).proceed();
        verifyLogMessage("Aggregate ID is null, proceeding without monitoring", Level.WARN);
    }

    @Test
    void monitorAggregateChanges_ShouldProceedWithoutMonitoring_WhenSnapshotCaptureFails() throws Throwable {
        // Given
        Object expectedResult = new Object();
        RuntimeException snapshotException = new RuntimeException("Snapshot failed");
        when(joinPoint.proceed()).thenReturn(expectedResult);
        doThrow(snapshotException).when(aggregateChangeDetectorService)
                .captureAggregateSnapshot(testWorkflow);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(joinPoint).proceed();
        verify(aggregateChangeDetectorService, never()).detectAggregateChanges(any(), any(), anyInt());
        verifyLogMessage("Failed to capture aggregate snapshot", Level.ERROR);
    }

    @Test
    void monitorAggregateChanges_ShouldPerformOptimizedUpdate_WhenChangesDetected() throws Throwable {
        // Given
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(true);

        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(
                eq(testWorkflow), any(String[].class), anyInt()))
                .thenReturn(changeMetadata);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);

        // Verify execution order
        var inOrder = inOrder(aggregateChangeDetectorService, optimizedAggregateUpdateRepository);
        inOrder.verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        inOrder.verify(aggregateChangeDetectorService).detectAggregateChanges(
                eq(testWorkflow), eq(trackFieldChanges.excludeFields()), eq(trackFieldChanges.maxDepth()));
        inOrder.verify(optimizedAggregateUpdateRepository).updateAggregateSelectively(changeMetadata);
        inOrder.verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);

        verifyLogMessage("Optimized aggregate update performed", Level.INFO);
    }

    @Test
    void monitorAggregateChanges_ShouldSkipUpdate_WhenNoChangesDetected() throws Throwable {
        // Given
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(false);

        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(
                eq(testWorkflow), any(String[].class), anyInt()))
                .thenReturn(changeMetadata);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(optimizedAggregateUpdateRepository, never()).updateAggregateSelectively(any(AggregateChangeMetadata.class));
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
        verifyLogMessage("No aggregate changes detected", Level.DEBUG);
    }

    // Exception handling tests

    @Test
    void monitorAggregateChanges_ShouldClearSnapshot_EvenWhenExceptionThrown() throws Throwable {
        // Given
        RuntimeException expectedException = new RuntimeException("Method failed");
        when(joinPoint.proceed()).thenThrow(expectedException);

        // When/Then
        assertThatThrownBy(() -> aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow))
                .isSameAs(expectedException)
                .hasMessage("Method failed");

        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
    }

    @Test
    void monitorAggregateChanges_ShouldClearSnapshot_EvenWhenDetectionThrowsException() throws Throwable {
        // Given
        Object expectedResult = new Object();
        RuntimeException detectionException = new RuntimeException("Detection failed");
        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(any(), any(), anyInt()))
                .thenThrow(detectionException);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
        verify(optimizedAggregateUpdateRepository, never()).updateAggregateSelectively(any(AggregateChangeMetadata.class));
        verifyLogMessage("Failed to detect aggregate changes", Level.ERROR);
    }

    @Test
    void monitorAggregateChanges_ShouldClearSnapshot_EvenWhenUpdateThrowsException() throws Throwable {
        // Given
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(true);
        RuntimeException updateException = new RuntimeException("Update failed");

        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(any(), any(), anyInt()))
                .thenReturn(changeMetadata);
        doThrow(updateException).when(optimizedAggregateUpdateRepository)
                .updateAggregateSelectively(changeMetadata);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
        verify(optimizedAggregateUpdateRepository).updateAggregateSelectively(changeMetadata);
        verifyLogMessage("Failed to perform optimized aggregate update", Level.ERROR);
    }

    @Test
    void monitorAggregateChanges_ShouldClearSnapshot_EvenWhenClearSnapshotFails() throws Throwable {
        // Given
        Object expectedResult = new Object();
        RuntimeException clearException = new RuntimeException("Clear failed");
        when(joinPoint.proceed()).thenReturn(expectedResult);
        doThrow(clearException).when(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
        verifyLogMessage("Failed to clear aggregate snapshot", Level.WARN);
    }

    // Timeout tests

    @Test
    void monitorAggregateChanges_ShouldSkipChangeDetection_WhenTimeoutExceeded() throws Throwable {
        // Given
        TrackFieldChanges shortTimeoutAnnotation = createTrackFieldChangesAnnotation(true, true, 50L, new String[0], 10);
        Object expectedResult = new Object();

        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(100); // Exceed timeout
            return expectedResult;
        });

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, shortTimeoutAnnotation, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService, never()).detectAggregateChanges(any(), any(), anyInt());
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
        verifyLogMessage("exceeding timeout", Level.WARN);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 10L})
    void monitorAggregateChanges_ShouldHandleVariousTimeouts(long timeoutMs) throws Throwable {
        // Given
        TrackFieldChanges timeoutAnnotation = createTrackFieldChangesAnnotation(true, true, timeoutMs, new String[0], 10);
        Object expectedResult = new Object();

        when(joinPoint.proceed()).thenAnswer(invocation -> {
            if (timeoutMs > 0) {
                Thread.sleep(timeoutMs + 10); // Exceed timeout
            }
            return expectedResult;
        });

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, timeoutAnnotation, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
    }

    // Boundary value tests

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 100, Integer.MAX_VALUE})
    void monitorAggregateChanges_ShouldHandleVariousMaxDepths(int maxDepth) throws Throwable {
        // Given
        TrackFieldChanges depthAnnotation = createTrackFieldChangesAnnotation(true, true, 5000L, new String[0], maxDepth);
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(false);

        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(any(), any(), eq(maxDepth)))
                .thenReturn(changeMetadata);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, depthAnnotation, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).detectAggregateChanges(testWorkflow, new String[0], maxDepth);
    }

    @Test
    void monitorAggregateChanges_ShouldHandleEmptyExcludeFields() throws Throwable {
        // Given
        String[] emptyExcludeFields = new String[0];
        TrackFieldChanges annotation = createTrackFieldChangesAnnotation(true, true, 5000L, emptyExcludeFields, 10);
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(false);

        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(any(), any(), anyInt()))
                .thenReturn(changeMetadata);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, annotation, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).detectAggregateChanges(testWorkflow, emptyExcludeFields, 10);
    }

    @Test
    void monitorAggregateChanges_ShouldHandleNullExcludeFields() throws Throwable {
        // Given
        TrackFieldChanges annotation = createTrackFieldChangesAnnotation(true, true, 5000L, null, 10);
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(false);

        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(any(), any(), anyInt()))
                .thenReturn(changeMetadata);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, annotation, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).detectAggregateChanges(eq(testWorkflow), isNull(), eq(10));
    }

    // Concurrent access tests

    @Test
    void monitorAggregateChanges_ShouldHandleConcurrentAccess() throws Throwable {
        // Given
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Throwable> exception = new AtomicReference<>();

        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // When
        for (int i = 0; i < threadCount; i++) {
            final Workflow workflow = createTestWorkflow("workflow-" + i, "ext-" + i);
            executor.submit(() -> {
                try {
                    Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, workflow);
                    assertThat(result).isEqualTo(expectedResult);
                    successCount.incrementAndGet();
                } catch (Throwable e) {
                    exception.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(exception.get()).isNull();
        assertThat(successCount.get()).isEqualTo(threadCount);
        verify(aggregateChangeDetectorService, times(threadCount)).captureAggregateSnapshot(any());
        verify(aggregateChangeDetectorService, times(threadCount)).clearAggregateSnapshot(any());
    }

    // Performance tests

    @Test
    void monitorAggregateChanges_ShouldCompleteWithinReasonableTime() throws Throwable {
        // Given
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(true);

        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(any(), any(), anyInt()))
                .thenReturn(changeMetadata);

        // When
        Instant start = Instant.now();
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);
        Duration elapsed = Duration.between(start, Instant.now());

        // Then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1)); // Should complete quickly
    }

    // Edge case tests

    @Test
    void monitorAggregateChanges_ShouldHandleVeryLargeWorkflow() throws Throwable {
        // Given
        Workflow largeWorkflow = createLargeTestWorkflow();
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(false);

        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(any(), any(), anyInt()))
                .thenReturn(changeMetadata);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, largeWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(largeWorkflow);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(largeWorkflow);
    }

    @Test
    void monitorAggregateChanges_ShouldHandleNullChangeMetadata() throws Throwable {
        // Given
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(any(), any(), anyInt()))
                .thenReturn(null);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(optimizedAggregateUpdateRepository, never()).updateAggregateSelectively(any(AggregateChangeMetadata.class));
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
    }

    // Helper methods

    private TrackFieldChanges createTrackFieldChangesAnnotation() {
        return createTrackFieldChangesAnnotation(true, true, 5000L, new String[0], 10);
    }

    private TrackFieldChanges createTrackFieldChangesAnnotation(boolean isAggregateRoot,
                                                                boolean deepComparison,
                                                                long timeoutMs,
                                                                String[] excludeFields,
                                                                int maxDepth) {
        return new TrackFieldChanges() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return TrackFieldChanges.class;
            }

            @Override
            public boolean isAggregateRoot() {
                return isAggregateRoot;
            }

            @Override
            public boolean deepComparison() {
                return deepComparison;
            }

            @Override
            public String[] excludeFields() {
                return excludeFields != null ? excludeFields : new String[0];
            }

            @Override
            public int maxDepth() {
                return maxDepth;
            }

            @Override
            public long timeoutMs() {
                return timeoutMs;
            }
        };
    }

    private Workflow createTestWorkflow() {
        return createTestWorkflow("Test Workflow", "ext-workflow-1");
    }

    private Workflow createTestWorkflow(String name, String externalId) {
        return Workflow.builder()
                .workflowId(1L)
                .name(name)
                .description("Test Description")
                .statusId(1L)
                .externalWorkflowId(externalId)
                .build();
    }

    private Workflow createLargeTestWorkflow() {
        return Workflow.builder()
                .workflowId(1L)
                .name("Large Test Workflow with very long name that exceeds normal limits")
                .description("A very detailed description that contains a lot of information about the workflow and its purpose in the system")
                .statusId(1L)
                .externalWorkflowId("ext-large-workflow-1")
                .build();
    }

    private AggregateChangeMetadata createAggregateChangeMetadata(boolean hasChanges) {
        AggregateChangeMetadata.AggregateChangeMetadataBuilder builder = AggregateChangeMetadata.builder()
                .aggregateId("Workflow:1")
                .aggregateType("Workflow")
                .changeTimestamp(Instant.now());

        if (hasChanges) {
            // Add some mock changes
            AggregateChangeMetadata.ChildEntityChange childChange =
                    AggregateChangeMetadata.ChildEntityChange.builder()
                            .childId("task-1")
                            .childType("TaskEntity")
                            .changeType(AggregateChangeMetadata.ChangeType.MODIFIED)
                            .build();
            builder.addedChildren(List.of(childChange));
        } else {
            // Ensure empty collections for no changes
            builder.addedChildren(Collections.emptyList())
                    .removedChildren(Collections.emptyList())
                    .modifiedChildren(Collections.emptyList());
        }

        return builder.build();
    }

    private void verifyLogMessage(String expectedMessage, Level expectedLevel) {
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains(expectedMessage));

        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel().equals(expectedLevel));
    }
}