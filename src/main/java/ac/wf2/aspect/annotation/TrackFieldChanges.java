package ac.wf2.aspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackFieldChanges {
    /**
     * Specify if this is an aggregator root entity
     */
    boolean isAggregateRoot() default false;
    
    /**
     * Specify child entities to track for aggregator operations
     */
    String[] childEntities() default {};
    
    /**
     * Enable deep field comparison for nested objects
     */
    boolean deepComparison() default false;
}
