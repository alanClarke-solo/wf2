package ac.workflow.integration;

import ac.workflow.domain.enums.TaskStatus;
import ac.workflow.domain.model.Task;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.WorkflowRepository;
import ac.workflow.service.EnhancedWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "spring.redis.enabled=false"
})
class WorkflowServiceIntegrationTest {

    @Autowired
    private EnhancedWorkflowService workflowService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @BeforeEach
    void setUp() {
        // Clean up any existing data
        workflowRepository.deleteAll();
    }

    @Test
    @Transactional
    void createWorkflow_ShouldPersistWorkflowWithTasks() {
        // Given
        Workflow workflow = Workflow.builder()
                .name("Integration Test Workflow")
                .description("Test Description")
                .statusId(1L)
                .tasks(new HashSet<>())
                .build();

        // When
        Workflow savedWorkflow = workflowService.createWorkflow(workflow);

        // Then
        assertThat(savedWorkflow).isNotNull();
        assertThat(savedWorkflow.getWorkflowId()).isNotNull();
        assertThat(savedWorkflow.getCreatedAt()).isNotNull();
        assertThat(savedWorkflow.getUpdatedAt()).isNotNull();
        assertThat(savedWorkflow.getName()).isEqualTo("Integration Test Workflow");
        assertThat(savedWorkflow.getDescription()).isEqualTo("Test Description");
        assertThat(savedWorkflow.getStatusId()).isEqualTo(1L);

        // Verify persistence - Spring Data JDBC doesn't need flush()
        // Data is immediately committed with @Transactional
        Workflow foundWorkflow = workflowRepository.findById(savedWorkflow.getWorkflowId()).orElse(null);
        assertThat(foundWorkflow).isNotNull();
        assertThat(foundWorkflow.getName()).isEqualTo("Integration Test Workflow");
        assertThat(foundWorkflow.getWorkflowId()).isEqualTo(savedWorkflow.getWorkflowId());
    }

    @Test
    @Transactional
    void updateWorkflowStatus_ShouldTriggerAspectMonitoring() {
        // Given
        Workflow workflow = createAndSaveTestWorkflow();
        Long originalStatusId = workflow.getStatusId();
        Long newStatusId = 2L;

        // Ensure we're actually changing the status
        assertThat(newStatusId).isNotEqualTo(originalStatusId);

        // When
        Workflow updatedWorkflow = workflowService.updateWorkflowStatus(workflow, newStatusId);

        // Then
        assertThat(updatedWorkflow).isNotNull();
        assertThat(updatedWorkflow.getStatusId()).isEqualTo(newStatusId);
        assertThat(updatedWorkflow.getUpdatedAt()).isNotNull();
        assertThat(updatedWorkflow.getWorkflowId()).isEqualTo(workflow.getWorkflowId());

        // Verify persistence - remove flush() call
        Workflow foundWorkflow = workflowRepository.findById(workflow.getWorkflowId()).orElse(null);
        assertThat(foundWorkflow).isNotNull();
        assertThat(foundWorkflow.getStatusId()).isEqualTo(newStatusId);
        assertThat(foundWorkflow.getUpdatedAt()).isAfterOrEqualTo(workflow.getUpdatedAt());
    }

    @Test
    @Transactional
    void addTaskToWorkflow_ShouldPersistTaskRelationship() {
        // Given
        Workflow workflow = createAndSaveTestWorkflow();
        assertThat(workflow.getTasks()).isEmpty();
        Task task = createValidTask(workflow.getWorkflowId());

        // When
        Workflow updatedWorkflow = workflowService.addTaskToWorkflow(workflow, task);

        // Then
        assertThat(updatedWorkflow).isNotNull();
        assertThat(updatedWorkflow.getWorkflowId()).isEqualTo(workflow.getWorkflowId());

        // Remove flush() call - not needed in Spring Data JDBC
        Workflow foundWorkflow = workflowRepository.findById(workflow.getWorkflowId()).orElse(null);
        assertThat(foundWorkflow).isNotNull();

        Set<Task> tasks = foundWorkflow.getTasks();
        assertThat(tasks).isNotNull().hasSize(1);

        Task foundTask = tasks.iterator().next();
        assertThat(foundTask.getWorkflowId()).isEqualTo(workflow.getWorkflowId());
        assertThat(foundTask.getTaskDefId()).isEqualTo(task.getTaskDefId());
        assertThat(foundTask.getStatusId()).isEqualTo(task.getStatusId());
        assertThat(foundWorkflow.getTasks()).contains(foundTask);
    }

    @Test
    @Transactional
    void updateWorkflowDetails_ShouldPersistChanges() {
        // Given
        Workflow workflow = createAndSaveTestWorkflow();
        String newName = "Updated Workflow Name";
        String newDescription = "Updated Description";

        // When
        Workflow updatedWorkflow = workflowService.updateWorkflowDetails(workflow, newName, newDescription);

        // Then
        assertThat(updatedWorkflow).isNotNull();
        assertThat(updatedWorkflow.getName()).isEqualTo(newName);
        assertThat(updatedWorkflow.getDescription()).isEqualTo(newDescription);
        assertThat(updatedWorkflow.getUpdatedAt()).isNotNull();

        // Remove flush() call
        Workflow foundWorkflow = workflowRepository.findById(workflow.getWorkflowId()).orElse(null);
        assertThat(foundWorkflow).isNotNull();
        assertThat(foundWorkflow.getName()).isEqualTo(newName);
        assertThat(foundWorkflow.getDescription()).isEqualTo(newDescription);
    }

    @Test
    @Transactional
    void updateWorkflowDetails_ShouldNotSaveWhenNoChanges() {
        // Given
        Workflow workflow = createAndSaveTestWorkflow();
        String currentName = workflow.getName();
        String currentDescription = workflow.getDescription();
        OffsetDateTime originalUpdatedAt = workflow.getUpdatedAt();

        // When
        Workflow result = workflowService.updateWorkflowDetails(workflow, currentName, currentDescription);

        // Then
        assertThat(result).isEqualTo(workflow);
        assertThat(result.getUpdatedAt()).isEqualTo(originalUpdatedAt);

        // Remove flush() call
        Workflow foundWorkflow = workflowRepository.findById(workflow.getWorkflowId()).orElse(null);
        assertThat(foundWorkflow).isNotNull();
        assertThat(foundWorkflow.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    @Transactional
    void findById_ShouldReturnPersistedWorkflow() {
        // Given
        Workflow workflow = createAndSaveTestWorkflow();
        // Remove flush() call

        // When
        var foundWorkflow = workflowService.findById(workflow.getWorkflowId());

        // Then
        assertThat(foundWorkflow).isPresent();
        assertThat(foundWorkflow.get().getWorkflowId()).isEqualTo(workflow.getWorkflowId());
        assertThat(foundWorkflow.get().getName()).isEqualTo(workflow.getName());
    }

    @Test
    @Transactional
    void findByStatusId_ShouldReturnWorkflowsWithStatus() {
        // Given
        Workflow workflow1 = createAndSaveTestWorkflowWithStatus(1L);
        Workflow workflow2 = createAndSaveTestWorkflowWithStatus(1L);
        Workflow workflow3 = createAndSaveTestWorkflowWithStatus(2L);
        // Remove flush() call

        // When
        var workflowsWithStatus1 = workflowService.findByStatusId(1L);
        var workflowsWithStatus2 = workflowService.findByStatusId(2L);

        // Then
        assertThat(workflowsWithStatus1).hasSize(2);
        assertThat(workflowsWithStatus2).hasSize(1);
        assertThat(workflowsWithStatus1).extracting(Workflow::getWorkflowId)
                .containsExactlyInAnyOrder(workflow1.getWorkflowId(), workflow2.getWorkflowId());
        assertThat(workflowsWithStatus2).extracting(Workflow::getWorkflowId)
                .containsExactly(workflow3.getWorkflowId());
    }

    private Workflow createAndSaveTestWorkflow() {
        return createAndSaveTestWorkflowWithStatus(1L);
    }

    private Workflow createAndSaveTestWorkflowWithStatus(Long statusId) {
        Workflow workflow = Workflow.builder()
                .name("Test Workflow")
                .description("Test Description")
                .statusId(statusId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))  // Use UTC
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC)) // Use UTC
                .tasks(new HashSet<>())
                .build();

        return workflowRepository.save(workflow);
    }

    private Task createValidTask(Long workflowId) {
        return Task.builder()
                .workflowId(workflowId)
                .taskDefId(1L)
                .statusId(TaskStatus.STARTING.getId())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))  // Use UTC
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC)) // Use UTC
                .build();
    }
}