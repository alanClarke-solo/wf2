package ac.workflow.aspect.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class AggregateChangeMetadataTest {

    private AggregateChangeMetadata aggregateChangeMetadata;

    @BeforeEach
    void setUp() {
        aggregateChangeMetadata = AggregateChangeMetadata.builder()
                .aggregateId("aggregate-1")
                .aggregateType("WorkflowAggregate")
                .changeTimestamp(Instant.now())
                .build();
    }

    @Test
    void builder_ShouldCreateValidMetadata() {
        // Given/When
        AggregateChangeMetadata metadata = AggregateChangeMetadata.builder()
                .aggregateId("agg-1")
                .aggregateType("TestAggregate")
                .changeTimestamp(Instant.now())
                .build();

        // Then
        assertThat(metadata.getAggregateId()).isEqualTo("agg-1");
        assertThat(metadata.getAggregateType()).isEqualTo("TestAggregate");
        assertThat(metadata.getModifiedRootFields()).isEmpty();
        assertThat(metadata.getAddedChildren()).isEmpty();
        assertThat(metadata.getRemovedChildren()).isEmpty();
        assertThat(metadata.getModifiedChildren()).isEmpty();
    }

    @Test
    void hasRootChanges_ShouldReturnFalse_WhenNoRootChanges() {
        // When
        boolean result = aggregateChangeMetadata.hasRootChanges();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasRootChanges_ShouldReturnTrue_WhenRootFieldsModified() {
        // Given
        Set<String> modifiedFields = new HashSet<>();
        modifiedFields.add("name");
        aggregateChangeMetadata.setModifiedRootFields(modifiedFields);

        // When
        boolean result = aggregateChangeMetadata.hasRootChanges();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasChildChanges_ShouldReturnFalse_WhenNoChildChanges() {
        // When
        boolean result = aggregateChangeMetadata.hasChildChanges();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasChildChanges_ShouldReturnTrue_WhenChildrenAdded() {
        // Given
        AggregateChangeMetadata.ChildEntityChange childChange = createChildEntityChange();
        aggregateChangeMetadata.addChildChange(childChange);

        // When
        boolean result = aggregateChangeMetadata.hasChildChanges();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasAnyChanges_ShouldReturnTrue_WhenRootChangesExist() {
        // Given
        Set<String> modifiedFields = new HashSet<>();
        modifiedFields.add("status");
        aggregateChangeMetadata.setModifiedRootFields(modifiedFields);

        // When
        boolean result = aggregateChangeMetadata.hasAnyChanges();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasAnyChanges_ShouldReturnTrue_WhenChildChangesExist() {
        // Given
        AggregateChangeMetadata.ChildEntityChange childChange = createChildEntityChange();
        aggregateChangeMetadata.addChildChange(childChange);

        // When
        boolean result = aggregateChangeMetadata.hasAnyChanges();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasAnyChanges_ShouldReturnFalse_WhenNoChanges() {
        // When
        boolean result = aggregateChangeMetadata.hasAnyChanges();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void getTotalChangeCount_ShouldReturnCorrectCount() {
        // Given
        Set<String> modifiedFields = new HashSet<>();
        modifiedFields.add("field1");
        modifiedFields.add("field2");
        aggregateChangeMetadata.setModifiedRootFields(modifiedFields);

        AggregateChangeMetadata.ChildEntityChange addedChild = createChildEntityChange();
        AggregateChangeMetadata.ChildEntityChange modifiedChild = createChildEntityChange();
        aggregateChangeMetadata.addChildChange(addedChild);
        aggregateChangeMetadata.addChildChange(modifiedChild);

        // When
        int totalCount = aggregateChangeMetadata.getTotalChangeCount();

        // Then
        assertThat(totalCount).isEqualTo(4); // 2 root fields + 2 child changes
    }

    @Test
    void addChildChange_ShouldAddToCorrectList_BasedOnChangeType() {
        // Given
        AggregateChangeMetadata.ChildEntityChange addedChild = AggregateChangeMetadata.ChildEntityChange.builder()
                .childId("child-1")
                .childType("TaskEntity")
                .changeType(AggregateChangeMetadata.ChangeType.ADDED)
                .build();

        AggregateChangeMetadata.ChildEntityChange removedChild = AggregateChangeMetadata.ChildEntityChange.builder()
                .childId("child-2")
                .childType("TaskEntity")
                .changeType(AggregateChangeMetadata.ChangeType.REMOVED)
                .build();

        AggregateChangeMetadata.ChildEntityChange modifiedChild = AggregateChangeMetadata.ChildEntityChange.builder()
                .childId("child-3")
                .childType("TaskEntity")
                .changeType(AggregateChangeMetadata.ChangeType.MODIFIED)
                .build();

        // When
        aggregateChangeMetadata.addChildChange(addedChild);
        aggregateChangeMetadata.addChildChange(removedChild);
        aggregateChangeMetadata.addChildChange(modifiedChild);

        // Then
        assertThat(aggregateChangeMetadata.getAddedChildren()).contains(addedChild);
        assertThat(aggregateChangeMetadata.getRemovedChildren()).contains(removedChild);
        assertThat(aggregateChangeMetadata.getModifiedChildren()).contains(modifiedChild);
    }

    @Test
    void getChangeSummary_ShouldReturnMeaningfulSummary() {
        // Given
        Set<String> modifiedFields = new HashSet<>();
        modifiedFields.add("name");
        aggregateChangeMetadata.setModifiedRootFields(modifiedFields);

        AggregateChangeMetadata.ChildEntityChange childChange = createChildEntityChange();
        aggregateChangeMetadata.addChildChange(childChange);

        // When
        String summary = aggregateChangeMetadata.getChangeSummary();

        // Then
        assertThat(summary).isNotBlank();
        assertThat(summary).contains("1"); // Should mention counts
    }

    @Test
    void childEntityChange_ShouldHaveCorrectProperties() {
        // Given
        Set<String> modifiedFields = new HashSet<>();
        modifiedFields.add("status");
        
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("status", "PENDING");
        
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("status", "COMPLETED");

        // When
        AggregateChangeMetadata.ChildEntityChange childChange = AggregateChangeMetadata.ChildEntityChange.builder()
                .childId("task-1")
                .childType("TaskEntity")
                .childFieldName("tasks")
                .modifiedFields(modifiedFields)
                .oldValues(oldValues)
                .newValues(newValues)
                .changeType(AggregateChangeMetadata.ChangeType.MODIFIED)
                .build();

        // Then
        assertThat(childChange.getChildId()).isEqualTo("task-1");
        assertThat(childChange.getChildType()).isEqualTo("TaskEntity");
        assertThat(childChange.getChildFieldName()).isEqualTo("tasks");
        assertThat(childChange.getChangeType()).isEqualTo(AggregateChangeMetadata.ChangeType.MODIFIED);
        assertThat(childChange.hasModifiedFields()).isTrue();
        assertThat(childChange.getModifiedFieldCount()).isEqualTo(1);
    }

    @Test
    void childEntityChange_GettersReturnImmutableCollections() {
        // Given
        AggregateChangeMetadata.ChildEntityChange childChange = createChildEntityChange();

        // When/Then
        assertThatThrownBy(() -> childChange.getModifiedFields().add("newField"))
                .isInstanceOf(UnsupportedOperationException.class);
        
        assertThatThrownBy(() -> childChange.getOldValues().put("newKey", "newValue"))
                .isInstanceOf(UnsupportedOperationException.class);
        
        assertThatThrownBy(() -> childChange.getNewValues().put("newKey", "newValue"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private AggregateChangeMetadata.ChildEntityChange createChildEntityChange() {
        return AggregateChangeMetadata.ChildEntityChange.builder()
                .childId("child-1")
                .childType("TaskEntity")
                .childFieldName("tasks")
                .changeType(AggregateChangeMetadata.ChangeType.ADDED)
                .build();
    }
}
