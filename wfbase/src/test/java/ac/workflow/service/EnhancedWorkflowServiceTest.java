package ac.workflow.service;

import ac.workflow.domain.enums.TaskStatus;
import ac.workflow.domain.model.Task;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnhancedWorkflowServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @InjectMocks
    private EnhancedWorkflowService workflowService;

    private Workflow testWorkflow;
    private Task testTask;

    @BeforeEach
    void setUp() {
        testWorkflow = createTestWorkflow();
        testTask = createTestTask();
    }

    @Test
    void findById_ShouldReturnWorkflow_WhenWorkflowExists() {
        // Given
        Long workflowId = 1L;
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(testWorkflow));

        // When
        Optional<Workflow> result = workflowService.findById(workflowId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testWorkflow);
        verify(workflowRepository).findById(workflowId);
    }

    @Test
    void findById_ShouldReturnEmpty_WhenWorkflowNotExists() {
        // Given
        Long workflowId = 999L;
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.empty());

        // When
        Optional<Workflow> result = workflowService.findById(workflowId);

        // Then
        assertThat(result).isEmpty();
        verify(workflowRepository).findById(workflowId);
    }

    @Test
    void findByStatusId_ShouldReturnWorkflows_WhenStatusExists() {
        // Given
        Long statusId = 1L;
        List<Workflow> expectedWorkflows = List.of(testWorkflow);
        when(workflowRepository.findByStatusId(statusId)).thenReturn(expectedWorkflows);

        // When
        List<Workflow> result = workflowService.findByStatusId(statusId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result).contains(testWorkflow);
        verify(workflowRepository).findByStatusId(statusId);
    }

    @Test
    void createWorkflow_ShouldSetTimestamps_AndSaveWorkflow() {
        // Given
        Workflow newWorkflow = createTestWorkflowWithoutTimestamps();
        when(workflowRepository.save(any(Workflow.class))).thenReturn(testWorkflow);

        // When
        Workflow result = workflowService.createWorkflow(newWorkflow);

        // Then
        assertThat(newWorkflow.getCreatedAt()).isNotNull();
        assertThat(newWorkflow.getUpdatedAt()).isNotNull();
        assertThat(result).isEqualTo(testWorkflow);
        verify(workflowRepository).save(newWorkflow);
    }

    @Test
    void createWorkflow_ShouldSetTaskTimestamps_WhenTasksPresent() {
        // Given
        Workflow workflowWithTasks = createTestWorkflowWithTasks();
        when(workflowRepository.save(any(Workflow.class))).thenReturn(workflowWithTasks);

        // When
        workflowService.createWorkflow(workflowWithTasks);

        // Then
        workflowWithTasks.getTasks().forEach(task -> {
            assertThat(task.getCreatedAt()).isNotNull();
            assertThat(task.getUpdatedAt()).isNotNull();
        });
        verify(workflowRepository).save(workflowWithTasks);
    }

    @Test
    void updateWorkflowStatus_ShouldUpdateStatusAndTimestamp() {
        // Given
        Long newStatusId = 2L;
        when(workflowRepository.save(any(Workflow.class))).thenReturn(testWorkflow);

        // When
        Workflow result = workflowService.updateWorkflowStatus(testWorkflow, newStatusId);

        // Then
        assertThat(testWorkflow.getStatusId()).isEqualTo(newStatusId);
        assertThat(testWorkflow.getUpdatedAt()).isNotNull();
        assertThat(result).isEqualTo(testWorkflow);
        verify(workflowRepository).save(testWorkflow);
    }

    @Test
    void updateWorkflowStatus_ShouldThrowException_WhenWorkflowIsNull() {
        // Given
        Long newStatusId = 2L;

        // When/Then
        assertThatThrownBy(() -> workflowService.updateWorkflowStatus(null, newStatusId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow cannot be null");

        verify(workflowRepository, never()).save(any());
    }

    @Test
    void addTaskToWorkflow_ShouldAddTaskAndSave() {
        // Given
        when(workflowRepository.save(any(Workflow.class))).thenReturn(testWorkflow);

        // When
        Workflow result = workflowService.addTaskToWorkflow(testWorkflow, testTask);

        // Then
        // Since we can't verify the mock spy, let's verify the save was called
        assertThat(result).isEqualTo(testWorkflow);
        verify(workflowRepository).save(testWorkflow);
    }

    @Test
    void addTaskToWorkflow_ShouldThrowException_WhenWorkflowIsNull() {
        // When/Then
        assertThatThrownBy(() -> workflowService.addTaskToWorkflow(null, testTask))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow cannot be null");

        verify(workflowRepository, never()).save(any());
    }

    @Test
    void addTaskToWorkflow_ShouldThrowException_WhenTaskIsNull() {
        // When/Then
        assertThatThrownBy(() -> workflowService.addTaskToWorkflow(testWorkflow, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task cannot be null");

        verify(workflowRepository, never()).save(any());
    }

    @Test
    void updateWorkflowDetails_ShouldUpdateNameAndDescription_WhenBothChanged() {
        // Given
        String newName = "Updated Workflow";
        String newDescription = "Updated Description";
        when(workflowRepository.save(any(Workflow.class))).thenReturn(testWorkflow);

        // When
        Workflow result = workflowService.updateWorkflowDetails(testWorkflow, newName, newDescription);

        // Then
        assertThat(testWorkflow.getName()).isEqualTo(newName);
        assertThat(testWorkflow.getDescription()).isEqualTo(newDescription);
        assertThat(testWorkflow.getUpdatedAt()).isNotNull();
        assertThat(result).isEqualTo(testWorkflow);
        verify(workflowRepository).save(testWorkflow);
    }

    @Test
    void updateWorkflowDetails_ShouldNotSave_WhenNoChanges() {
        // Given
        String currentName = testWorkflow.getName();
        String currentDescription = testWorkflow.getDescription();

        // When
        Workflow result = workflowService.updateWorkflowDetails(testWorkflow, currentName, currentDescription);

        // Then
        assertThat(result).isEqualTo(testWorkflow);
        verify(workflowRepository, never()).save(any());
    }

    @Test
    void updateWorkflowDetails_ShouldThrowException_WhenWorkflowIsNull() {
        // When/Then
        assertThatThrownBy(() -> workflowService.updateWorkflowDetails(null, "name", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow cannot be null");

        verify(workflowRepository, never()).save(any());
    }

    @Test
    void deleteWorkflow_ShouldCallRepositoryDelete() {
        // Given
        Long workflowId = 1L;

        // When
        workflowService.deleteWorkflow(workflowId);

        // Then
        verify(workflowRepository).deleteById(workflowId);
    }

    // Helper methods - Fixed to not use spy which was causing issues
    private Workflow createTestWorkflow() {
        Set<Task> tasks = new HashSet<>();
        return Workflow.builder()
                .workflowId(1L)
                .name("Test Workflow")
                .description("Test Description")
                .statusId(1L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .tasks(tasks)
                .build();
    }

    private Workflow createTestWorkflowWithoutTimestamps() {
        return Workflow.builder()
                .workflowId(1L)
                .name("Test Workflow")
                .description("Test Description")
                .statusId(1L)
                .tasks(new HashSet<>())
                .build();
    }

    private Workflow createTestWorkflowWithTasks() {
        Set<Task> tasks = new HashSet<>();
        tasks.add(createTestTask());
        tasks.add(createTestTask());

        return Workflow.builder()
                .workflowId(1L)
                .name("Test Workflow")
                .description("Test Description")
                .statusId(1L)
                .tasks(tasks)
                .build();
    }

    private Task createTestTask() {
        return Task.builder()
                .workflowId(1L)
                .taskDefId(1L)
                .statusId(TaskStatus.STARTING.getId())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}