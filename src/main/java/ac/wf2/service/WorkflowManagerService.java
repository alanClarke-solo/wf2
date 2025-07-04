package ac.wf2.service;

import ac.wf2.domain.dto.WorkflowConfigDto;
import ac.wf2.domain.enums.WorkflowStatus;
import ac.wf2.domain.model.Workflow;
import ac.wf2.repository.WorkflowRepository;
import ac.wf2.service.cache.WorkflowCacheService;
import ac.wf2.service.config.WorkflowConfigurationService;
import ac.wf2.service.event.WorkflowEventService;
import ac.wf2.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowManagerService {
    
    private final WorkflowRepository workflowRepository;
    private final WorkflowConfigurationService configurationService;
    private final WorkflowExecutionService executionService;
    private final WorkflowEventService eventService;
    private final NotificationService notificationService;
    private final WorkflowCacheService cacheService;
    
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