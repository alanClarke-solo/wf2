package ac.workflow.test;

import ac.workflow.domain.enums.TaskStatus;
import ac.workflow.domain.model.Task;
import ac.workflow.domain.model.Workflow;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;

@Component
public class TestDataHelper {
    
    public static Workflow createTestWorkflow(String name) {
        return Workflow.builder()
                .name(name)
                .description("Test Description for " + name)
                .statusId(1L)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .tasks(new HashSet<>())
                .build();
    }
    
    public static Workflow createTestWorkflowWithStatus(String name, Long statusId) {
        return Workflow.builder()
                .name(name)
                .description("Test Description for " + name)
                .statusId(statusId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .tasks(new HashSet<>())
                .build();
    }
    
    public static Task createTestTask(Long workflowId, Long taskDefId) {
        return Task.builder()
                .workflowId(workflowId)
                .taskDefId(taskDefId)
                .statusId(TaskStatus.STARTING.getId())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
    
    public static Task createTestTaskWithStatus(Long workflowId, Long taskDefId, Long statusId) {
        return Task.builder()
                .workflowId(workflowId)
                .taskDefId(taskDefId)
                .statusId(statusId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
