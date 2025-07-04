package ac.wf2.aspect.dto;

import lombok.Data;
import lombok.Builder;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class FieldChangeMetadata {
    private String entityId;
    private String entityType;
    private Set<String> modifiedFields;
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private Instant changeTimestamp;
    private boolean isAggregateRoot;
    private Map<String, FieldChangeMetadata> childChanges;
    
    public boolean hasChanges() {
        return !modifiedFields.isEmpty() || 
               (childChanges != null && !childChanges.isEmpty());
    }
}
