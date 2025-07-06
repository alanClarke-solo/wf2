package ac.workflow.aspect;

import ac.workflow.aspect.annotation.TrackFieldChanges;
import ac.workflow.aspect.dto.AggregateChangeMetadata;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.custom.OptimizedAggregateUpdateRepository;
import ac.workflow.service.monitoring.AggregateChangeDetectorService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    @BeforeEach
    void setUp() {
        trackFieldChanges = createTrackFieldChangesAnnotation();
        testWorkflow = createTestWorkflow();
        
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
    }

    @Test
    void monitorAggregateChanges_ShouldSkip_WhenNotAggregateRoot() throws Throwable {
        // Given
        TrackFieldChanges nonAggregateAnnotation = createTrackFieldChangesAnnotation(false, true, 1000L);
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, nonAggregateAnnotation, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService, never()).captureAggregateSnapshot(any());
        verify(joinPoint).proceed();
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
    }

    @Test
    void monitorAggregateChanges_ShouldSkip_WhenAggregateIdIsNull() throws Throwable {
        // Given
        Workflow workflowWithNullId = Workflow.builder().build();
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, workflowWithNullId);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService, never()).captureAggregateSnapshot(any());
        verify(joinPoint).proceed();
    }

    @Test
    void monitorAggregateChanges_ShouldProceedWithoutMonitoring_WhenSnapshotCaptureFails() throws Throwable {
        // Given
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);
        doThrow(new RuntimeException("Snapshot failed")).when(aggregateChangeDetectorService)
                .captureAggregateSnapshot(testWorkflow);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(joinPoint).proceed();
        verify(aggregateChangeDetectorService, never()).detectAggregateChanges(any(), any(), anyInt());
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
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService).detectAggregateChanges(
                eq(testWorkflow), eq(trackFieldChanges.excludeFields()), eq(trackFieldChanges.maxDepth()));
        verify(optimizedAggregateUpdateRepository).updateAggregateSelectively(changeMetadata);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
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
    }

    @Test
    void monitorAggregateChanges_ShouldClearSnapshot_EvenWhenExceptionThrown() throws Throwable {
        // Given
        RuntimeException expectedException = new RuntimeException("Method failed");
        when(joinPoint.proceed()).thenThrow(expectedException);

        // When/Then
        assertThatThrownBy(() -> aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow))
                .isEqualTo(expectedException);
        
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
    }

    @Test
    void monitorAggregateChanges_ShouldSkipChangeDetection_WhenTimeoutExceeded() throws Throwable {
        // Given
        TrackFieldChanges shortTimeoutAnnotation = createTrackFieldChangesAnnotation(true, true, 1L);
        Object expectedResult = new Object();
        
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(10); // Simulate slow execution
            return expectedResult;
        });

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, shortTimeoutAnnotation, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(aggregateChangeDetectorService).captureAggregateSnapshot(testWorkflow);
        verify(aggregateChangeDetectorService, never()).detectAggregateChanges(any(), any(), anyInt());
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
    }

    @Test
    void monitorAggregateChanges_ShouldHandleDetectionFailure_Gracefully() throws Throwable {
        // Given
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(
                eq(testWorkflow), any(String[].class), anyInt()))
                .thenThrow(new RuntimeException("Detection failed"));

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(optimizedAggregateUpdateRepository, never()).updateAggregateSelectively(any(AggregateChangeMetadata.class));
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
    }

    @Test
    void monitorAggregateChanges_ShouldHandleUpdateFailure_Gracefully() throws Throwable {
        // Given
        Object expectedResult = new Object();
        AggregateChangeMetadata changeMetadata = createAggregateChangeMetadata(true);
        
        when(joinPoint.proceed()).thenReturn(expectedResult);
        when(aggregateChangeDetectorService.detectAggregateChanges(
                eq(testWorkflow), any(String[].class), anyInt()))
                .thenReturn(changeMetadata);
        doThrow(new RuntimeException("Update failed")).when(optimizedAggregateUpdateRepository)
                .updateAggregateSelectively(changeMetadata);

        // When
        Object result = aspect.monitorAggregateChanges(joinPoint, trackFieldChanges, testWorkflow);

        // Then
        assertThat(result).isEqualTo(expectedResult);
        verify(optimizedAggregateUpdateRepository).updateAggregateSelectively(changeMetadata);
        verify(aggregateChangeDetectorService).clearAggregateSnapshot(testWorkflow);
    }

    // Helper methods
    private TrackFieldChanges createTrackFieldChangesAnnotation() {
        return createTrackFieldChangesAnnotation(true, true, 5000L);
    }

    private TrackFieldChanges createTrackFieldChangesAnnotation(boolean isAggregateRoot, 
                                                              boolean deepComparison, 
                                                              long timeoutMs) {
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
                return new String[0];
            }

            @Override
            public int maxDepth() {
                return 10;
            }

            @Override
            public long timeoutMs() {
                return timeoutMs;
            }
        };
    }

    private Workflow createTestWorkflow() {
        return Workflow.builder()
                .workflowId(1L)
                .name("Test Workflow")
                .description("Test Description")
                .statusId(1L)
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
        }

        return builder.build();
    }
}
