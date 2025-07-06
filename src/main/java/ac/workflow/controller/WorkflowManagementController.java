package ac.workflow.controller;

import ac.workflow.domain.dto.WorkflowStartRequest;
import ac.workflow.domain.dto.WorkflowStopRequest;
import ac.workflow.domain.dto.WorkflowUpdateRequest;
import ac.workflow.domain.enums.WorkflowStatus;
import ac.workflow.domain.model.Workflow;
import ac.workflow.service.WorkflowManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
public class WorkflowManagementController {
    
    private final WorkflowManagerService workflowManagerService;
    
    @GetMapping
    public ResponseEntity<List<String>> getAvailableWorkflows() {
        // Implementation would return available workflow configurations
        return ResponseEntity.ok(List.of("data-processing", "notification-workflow", "backup-workflow"));
    }
    
    @GetMapping("/{workflowId}")
    public ResponseEntity<Workflow> getWorkflowDetails(@PathVariable String workflowId) {
        return workflowManagerService.getWorkflowById(workflowId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/{workflowId}")
    public ResponseEntity<Workflow> updateWorkflow(@PathVariable String workflowId,
                                                  @RequestBody WorkflowUpdateRequest request) {
        // Implementation for updating workflow parameters
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/status")
    public ResponseEntity<List<Workflow>> getWorkflowsByStatus(@RequestParam WorkflowStatus status,
                                                              @RequestParam(required = false) String region) {
        if (region != null) {
            return ResponseEntity.ok(workflowManagerService.getWorkflowsByRegionAndStatus(region, status));
        }
        return ResponseEntity.ok(workflowManagerService.getRunningWorkflows());
    }
    
    @GetMapping("/running")
    public ResponseEntity<List<Workflow>> getRunningWorkflows() {
        return ResponseEntity.ok(workflowManagerService.getRunningWorkflows());
    }
    
    @PostMapping("/start")
    public ResponseEntity<Workflow> startWorkflow(@RequestBody WorkflowStartRequest request) {
        Workflow workflow = workflowManagerService.startWorkflow(request.getWorkflowConfigId(), request.getRegion());
        return ResponseEntity.ok(workflow);
    }
    
    @PostMapping("/{workflowId}/stop")
    public ResponseEntity<Void> stopWorkflow(@PathVariable Long workflowId,
                                           @RequestBody WorkflowStopRequest request) {
        workflowManagerService.stopWorkflow(workflowId, request.isImmediate());
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{workflowId}/restart")
    public ResponseEntity<Workflow> restartWorkflow(@PathVariable Long workflowId,
                                                   @RequestParam(required = false) String fromTaskId) {
        // Implementation for restarting workflow from specific task
        return ResponseEntity.ok().build();
    }


}