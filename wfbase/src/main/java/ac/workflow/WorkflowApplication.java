package ac.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Main application class for the Workflow Management System.
 * 
 * This application provides field change monitoring capabilities using AOP
 * to track modifications to domain entities and perform optimized database updates.
 * 
 * Features:
 * - Field change detection and monitoring
 * - Optimized database updates based on actual changes
 * - Aggregate root tracking for complex entity relationships
 * - Deep comparison support for nested objects
 * 
 * @author Workflow Team
 * @version 1.0
 */
@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class WorkflowApplication {
    
    /**
     * Main entry point for the application.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
