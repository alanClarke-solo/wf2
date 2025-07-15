package ac.workflow.service;

import ac.workflow.domain.dto.WorkflowConfigDto;
import ac.workflow.domain.enums.WorkflowStatus;
import ac.workflow.domain.model.Workflow;
import ac.workflow.repository.WorkflowRepository;
import ac.workflow.service.cache.WorkflowCacheService_1;
import ac.workflow.service.config.WorkflowConfigurationService;
import ac.workflow.service.event.WorkflowEventService;
import ac.workflow.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class WorkflowManagerService {
    
    private final WorkflowRepository workflowRepository;
    private final WorkflowConfigurationService configurationService;
    private final WorkflowExecutionService executionService;
    private final WorkflowEventService eventService;
    private final NotificationService notificationService;
    private final WorkflowCacheService_1 cacheService;

    public Workflow createWorkflow(String name) {
        Workflow workflow = new Workflow();
        workflow.setName(name);
        workflow.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        workflow.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return workflowRepository.save(workflow);
    }

    @Transactional
    public Workflow startWorkflow(String workflowConfigId, String region) {
        log.info("Starting workflow: {} for region: {}", workflowConfigId, region);
        
        WorkflowConfigDto config = configurationService.getWorkflowConfig(workflowConfigId);
        if (config == null) {
            throw new IllegalArgumentException("Workflow configuration not found: " + workflowConfigId);
        }
        
        // Create workflow instance
        Workflow workflow = new Workflow();
        workflow.setExternalWorkflowId(UUID.randomUUID().toString());
        workflow.setStatusId(WorkflowStatus.STARTING.getId());
        workflow.setStartTime(OffsetDateTime.now());
        workflow.setUpdatedBy("SYSTEM");
        workflow.setUpdatedAt(OffsetDateTime.now());
        
        workflow = workflowRepository.save(workflow);
        
        // Cache the workflow
        cacheService.cacheRunningWorkflow(workflow);
        
        // Send notification
        notificationService.sendWorkflowStatusNotification(workflow, WorkflowStatus.STARTING);
        
        // Start execution asynchronously
        executionService.executeWorkflowAsync(workflow.getWorkflowId(), config);
        
        return workflow;
    }
    
    @Transactional
    public void stopWorkflow(Long workflowId, boolean immediate) {
        log.info("Stopping workflow: {}, immediate: {}", workflowId, immediate);
        
        Optional<Workflow> workflowOpt = workflowRepository.findById(workflowId);
        if (workflowOpt.isEmpty()) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        
        Workflow workflow = workflowOpt.get();
        executionService.stopWorkflow(workflowId, immediate);
        
        workflow.setStatusId(WorkflowStatus.INTERRUPTED.getId());
        workflow.setEndTime(OffsetDateTime.now());
        workflow.setUpdatedAt(OffsetDateTime.now());
        workflowRepository.save(workflow);
        
        // Update cache
        cacheService.moveToCompletedWorkflows(workflow);
        
        // Send notification
        notificationService.sendWorkflowStatusNotification(workflow, WorkflowStatus.INTERRUPTED);
    }
    
    @Cacheable(value = "runningWorkflows")
    public List<Workflow> getRunningWorkflows() {
        return workflowRepository.findRunningWorkflows();
    }
    
    @Cacheable(value = "completedWorkflows", key = "#region + '_' + #status")
    public List<Workflow> getWorkflowsByRegionAndStatus(String region, WorkflowStatus status) {
        return workflowRepository.findByStatusId(status.getId());
    }
    
    public Optional<Workflow> getWorkflowById(String externalWorkflowId) {
        return workflowRepository.findByExternalWorkflowId(externalWorkflowId);
    }
}