package ac.workflow.controller;

import ac.workflow.service.admin.AdminConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final AdminConfigurationService adminConfigurationService;
    
    @PutMapping("/execution-parameters")
    public ResponseEntity<Void> updateExecutionParameters(@RequestBody Map<String, Object> parameters) {
        adminConfigurationService.updateExecutionParameters(parameters);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/cache-properties")
    public ResponseEntity<Void> updateCacheProperties(@RequestBody Map<String, Object> properties) {
        adminConfigurationService.updateCacheProperties(properties);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/schedules")
    public ResponseEntity<Void> updateSchedules(@RequestBody Map<String, String> schedules) {
        adminConfigurationService.updateSchedules(schedules);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/workflow-properties/{workflowId}")
    public ResponseEntity<Void> updateWorkflowProperties(@PathVariable String workflowId,
                                                        @RequestBody Map<String, Object> properties) {
        adminConfigurationService.updateWorkflowProperties(workflowId, properties);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/task-properties/{taskId}")
    public ResponseEntity<Void> updateTaskProperties(@PathVariable String taskId,
                                                    @RequestBody Map<String, Object> properties) {
        adminConfigurationService.updateTaskProperties(taskId, properties);
        return ResponseEntity.ok().build();
    }
}