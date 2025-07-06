package ac.workflow.aspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for field change monitoring.
 *
 * When applied to a method, this annotation triggers the field change monitoring
 * aspect to capture snapshots before and after method execution, detect changes,
 * and perform optimized database updates.
 *
 * Example usage:
 * <pre>
 * {@code
 * @TrackFieldChanges(isAggregateRoot = true, deepComparison = true)
 * public void updateWorkflow(Workflow workflow) {
 *     // Method implementation
 * }
 * }
 * </pre>
 *
 * @author Workflow Team
 * @version 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackFieldChanges {

    /**
     * Indicates if the tracked entity is an aggregate root.
     * Aggregate roots receive special handling for child entity changes.
     *
     * @return true if the entity is an aggregate root, false otherwise
     */
    boolean isAggregateRoot() default false;

    /**
     * Enables deep comparison for nested objects and collections.
     * When true, the system will recursively compare nested objects.
     *
     * @return true to enable deep comparison, false for shallow comparison
     */
    boolean deepComparison() default false;

    /**
     * Fields to exclude from change detection.
     * These fields will not be monitored for changes.
     *
     * @return array of field names to exclude
     */
    String[] excludeFields() default {};

    /**
     * Maximum depth for recursive comparison when deepComparison is enabled.
     * Prevents infinite recursion in complex object graphs.
     *
     * @return maximum comparison depth (default: 10)
     */
    int maxDepth() default 10;

    /**
     * Timeout in milliseconds for change detection operations.
     * If change detection takes longer than this, it will be skipped.
     *
     * @return timeout in milliseconds (default: 5000)
     */
    long timeoutMs() default 5000L;
}