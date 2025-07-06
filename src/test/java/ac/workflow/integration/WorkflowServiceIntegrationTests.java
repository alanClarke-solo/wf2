package ac.workflow.integration;

import ac.workflow.domain.model.Task;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.WorkflowRepository;
import ac.workflow.service.EnhancedWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowServiceIntegrationTest {

    @Autowired
    private EnhancedWorkflowService workflowService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Test
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

        // Verify persistence
        Workflow foundWorkflow = workflowRepository.findById(savedWorkflow.getWorkflowId()).orElse(null);
        assertThat(foundWorkflow).isNotNull();
        assertThat(foundWorkflow.getName()).isEqualTo("Integration Test Workflow");
    }

    @Test
    void updateWorkflowStatus_ShouldTriggerAspectMonitoring() {
        // Given
        Workflow workflow = createAndSaveTestWorkflow();
        Long newStatusId = 2L;

        // When
        Workflow updatedWorkflow = workflowService.updateWorkflowStatus(workflow, newStatusId);

        // Then
        assertThat(updatedWorkflow.getStatusId()).isEqualTo(newStatusId);
        assertThat(updatedWorkflow.getUpdatedAt()).isNotNull();
        
        // Verify persistence
        Workflow foundWorkflow = workflowRepository.findById(workflow.getWorkflowId()).orElse(null);
        assertThat(foundWorkflow).isNotNull();
        assertThat(foundWorkflow.getStatusId()).isEqualTo(newStatusId);
    }

    @Test
    void addTaskToWorkflow_ShouldPersistTaskRelationship() {
        // Given
        Workflow workflow = createAndSaveTestWorkflow();
        Task task = Task.builder()
                .workflowId(workflow.getWorkflowId())
                .taskDefId(1L)
                .statusId(1L) // Use statusId instead of status string
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        // When
        Workflow updatedWorkflow = workflowService.addTaskToWorkflow(workflow, task);

        // Then
        assertThat(updatedWorkflow).isNotNull();

        // Verify the task was added
        Workflow foundWorkflow = workflowRepository.findById(workflow.getWorkflowId()).orElse(null);
        assertThat(foundWorkflow).isNotNull();
        assertThat(foundWorkflow.getTasks()).isNotEmpty();
    }

    private Workflow createAndSaveTestWorkflow() {
        Workflow workflow = Workflow.builder()
                .name("Test Workflow")
                .description("Test Description")
                .statusId(1L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .tasks(new HashSet<>())
                .build();
        
        return workflowRepository.save(workflow);
    }
}
