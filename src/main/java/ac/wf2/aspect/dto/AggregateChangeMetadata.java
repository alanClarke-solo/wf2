package ac.wf2.aspect.dto;

import lombok.Data;
import lombok.Builder;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.List;

@Data
@Builder
public class AggregateChangeMetadata {
    private String aggregateId;
    private String aggregateType;
    private Set<String> modifiedRootFields;
    private Map<String, Object> oldRootValues;
    private Map<String, Object> newRootValues;
    private Instant changeTimestamp;
    
    // Child entity changes
    private List<ChildEntityChange> addedChildren;
    private List<ChildEntityChange> removedChildren;
    private List<ChildEntityChange> modifiedChildren;
    
    public boolean hasRootChanges() {
        return modifiedRootFields != null && !modifiedRootFields.isEmpty();
    }
    
    public boolean hasChildChanges() {
        return (addedChildren != null && !addedChildren.isEmpty()) ||
               (removedChildren != null && !removedChildren.isEmpty()) ||
               (modifiedChildren != null && !modifiedChildren.isEmpty());
    }
    
    public boolean hasAnyChanges() {
        return hasRootChanges() || hasChildChanges();
    }
    
    @Data
    @Builder
    public static class ChildEntityChange {
        private String childId;
        private String childType;
        private String childFieldName;
        private Object childEntity;
        private Set<String> modifiedFields;
        private Map<String, Object> oldValues;
        private Map<String, Object> newValues;
        private ChangeType changeType;
    }
    
    public enum ChangeType {
        ADDED, REMOVED, MODIFIED
    }
}
