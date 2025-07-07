package ac.workflow.controller;

import ac.workflow.domain.WorkflowSubmission;
import ac.workflow.service.WfRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    
    @Autowired
    private WfRouter wfRouter;
    
    @PostMapping("/submit")
    public ResponseEntity<Map<String, String>> submitWorkflow(
            @RequestParam String routeId,
            @RequestParam String workflowId,
            @RequestBody Map<String, Object> parameters) {
        
        try {
            String submissionId = wfRouter.submitWorkflow(routeId, workflowId, parameters);
            return ResponseEntity.ok(Map.of("submissionId", submissionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/status/{submissionId}")
    public ResponseEntity<WorkflowSubmission> getSubmissionStatus(@PathVariable String submissionId) {
        try {
            WorkflowSubmission submission = wfRouter.getSubmissionStatus(submissionId);
            return ResponseEntity.ok(submission);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<List<WorkflowSubmission>> getSubmissionsByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Map<String, Object> commonParams) {
        
        try {
            List<WorkflowSubmission> submissions = wfRouter.getSubmissionsByPeriod(from, to, commonParams);
            return ResponseEntity.ok(submissions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
