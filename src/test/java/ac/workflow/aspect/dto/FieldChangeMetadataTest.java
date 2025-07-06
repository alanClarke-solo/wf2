package ac.workflow.aspect.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FieldChangeMetadataTest {

    private FieldChangeMetadata fieldChangeMetadata;

    @BeforeEach
    void setUp() {
        fieldChangeMetadata = FieldChangeMetadata.builder()
                .entityId("test-entity-1")
                .entityType("TestEntity")
                .changeTimestamp(Instant.now())
                .isAggregateRoot(false)
                .build();
    }

    @Test
    void builder_ShouldCreateValidMetadata() {
        // Given/When
        FieldChangeMetadata metadata = FieldChangeMetadata.builder()
                .entityId("entity-1")
                .entityType("EntityType")
                .changeTimestamp(Instant.now())
                .isAggregateRoot(true)
                .build();

        // Then
        assertThat(metadata.getEntityId()).isEqualTo("entity-1");
        assertThat(metadata.getEntityType()).isEqualTo("EntityType");
        assertThat(metadata.isAggregateRoot()).isTrue();
        assertThat(metadata.getModifiedFields()).isEmpty();
        assertThat(metadata.getOldValues()).isEmpty();
        assertThat(metadata.getNewValues()).isEmpty();
        assertThat(metadata.getChildChanges()).isEmpty();
    }

    @Test
    void hasChanges_ShouldReturnFalse_WhenNoChanges() {
        // When
        boolean result = fieldChangeMetadata.hasChanges();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasChanges_ShouldReturnTrue_WhenFieldsModified() {
        // Given
        fieldChangeMetadata.addModifiedField("name");

        // When
        boolean result = fieldChangeMetadata.hasChanges();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasChanges_ShouldReturnTrue_WhenChildChangesExist() {
        // Given
        FieldChangeMetadata childMetadata = FieldChangeMetadata.builder()
                .entityId("child-1")
                .entityType("ChildEntity")
                .build();
        childMetadata.addModifiedField("childField");
        fieldChangeMetadata.addChildChange("child-1", childMetadata);

        // When
        boolean result = fieldChangeMetadata.hasChanges();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasModifiedFields_ShouldReturnTrue_WhenFieldsExist() {
        // Given
        fieldChangeMetadata.addModifiedField("testField");

        // When
        boolean result = fieldChangeMetadata.hasModifiedFields();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasChildChanges_ShouldReturnTrue_WhenChildChangesExist() {
        // Given
        FieldChangeMetadata childMetadata = FieldChangeMetadata.builder()
                .entityId("child-1")
                .entityType("ChildEntity")
                .build();
        fieldChangeMetadata.addChildChange("child-1", childMetadata);

        // When
        boolean result = fieldChangeMetadata.hasChildChanges();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void addModifiedField_ShouldAddField() {
        // Given
        String fieldName = "testField";

        // When
        fieldChangeMetadata.addModifiedField(fieldName);

        // Then
        assertThat(fieldChangeMetadata.getModifiedFields()).contains(fieldName);
        assertThat(fieldChangeMetadata.getModifiedFieldCount()).isEqualTo(1);
    }

    @Test
    void addModifiedField_ShouldIgnoreNullAndEmptyFields() {
        // When
        fieldChangeMetadata.addModifiedField(null);
        fieldChangeMetadata.addModifiedField("");
        fieldChangeMetadata.addModifiedField("   ");

        // Then
        assertThat(fieldChangeMetadata.getModifiedFields()).isEmpty();
    }

    @Test
    void addOldValue_ShouldAddValue() {
        // Given
        String fieldName = "testField";
        String oldValue = "oldValue";

        // When
        fieldChangeMetadata.addOldValue(fieldName, oldValue);

        // Then
        assertThat(fieldChangeMetadata.getOldValue(fieldName)).isEqualTo(oldValue);
    }

    @Test
    void addNewValue_ShouldAddValue() {
        // Given
        String fieldName = "testField";
        String newValue = "newValue";

        // When
        fieldChangeMetadata.addNewValue(fieldName, newValue);

        // Then
        assertThat(fieldChangeMetadata.getNewValue(fieldName)).isEqualTo(newValue);
    }

    @Test
    void addChildChange_ShouldAddChildChange() {
        // Given
        String childKey = "child-1";
        FieldChangeMetadata childMetadata = FieldChangeMetadata.builder()
                .entityId("child-1")
                .entityType("ChildEntity")
                .build();

        // When
        fieldChangeMetadata.addChildChange(childKey, childMetadata);

        // Then
        assertThat(fieldChangeMetadata.getChildChanges()).containsKey(childKey);
        assertThat(fieldChangeMetadata.getChildChangeCount()).isEqualTo(1);
    }

    @Test
    void isFieldModified_ShouldReturnTrue_WhenFieldModified() {
        // Given
        String fieldName = "testField";
        fieldChangeMetadata.addModifiedField(fieldName);

        // When
        boolean result = fieldChangeMetadata.isFieldModified(fieldName);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isFieldModified_ShouldReturnFalse_WhenFieldNotModified() {
        // Given
        String fieldName = "nonExistentField";

        // When
        boolean result = fieldChangeMetadata.isFieldModified(fieldName);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void merge_ShouldCombineMetadata() {
        // Given
        FieldChangeMetadata other = FieldChangeMetadata.builder()
                .entityId("other-entity")
                .entityType("OtherEntity")
                .build();
        other.addModifiedField("otherField");
        other.addOldValue("otherField", "otherOldValue");
        other.addNewValue("otherField", "otherNewValue");

        fieldChangeMetadata.addModifiedField("originalField");
        fieldChangeMetadata.addOldValue("originalField", "originalOldValue");

        // When
        fieldChangeMetadata.merge(other);

        // Then
        assertThat(fieldChangeMetadata.getModifiedFields())
                .contains("originalField", "otherField");
        assertThat(fieldChangeMetadata.getOldValue("originalField")).isEqualTo("originalOldValue");
        assertThat(fieldChangeMetadata.getOldValue("otherField")).isEqualTo("otherOldValue");
        assertThat(fieldChangeMetadata.getNewValue("otherField")).isEqualTo("otherNewValue");
    }

    @Test
    void getModifiedFields_ShouldReturnImmutableSet() {
        // Given
        fieldChangeMetadata.addModifiedField("testField");

        // When
        Set<String> modifiedFields = fieldChangeMetadata.getModifiedFields();

        // Then
        assertThatThrownBy(() -> modifiedFields.add("newField"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setModifiedFields_ShouldCreateDefensiveCopy() {
        // Given
        Set<String> originalSet = new HashSet<>();
        originalSet.add("field1");
        originalSet.add("field2");

        // When
        fieldChangeMetadata.setModifiedFields(originalSet);
        originalSet.add("field3"); // Modify original set

        // Then
        assertThat(fieldChangeMetadata.getModifiedFields())
                .containsExactlyInAnyOrder("field1", "field2")
                .doesNotContain("field3");
    }

    @Test
    void setOldValues_ShouldCreateDefensiveCopy() {
        // Given
        Map<String, Object> originalMap = new HashMap<>();
        originalMap.put("field1", "value1");

        // When
        fieldChangeMetadata.setOldValues(originalMap);
        originalMap.put("field2", "value2"); // Modify original map

        // Then
        assertThat(fieldChangeMetadata.getOldValues())
                .containsExactly(Map.entry("field1", "value1"))
                .doesNotContainKey("field2");
    }

    @Test
    void getChangeSummary_ShouldReturnMeaningfulSummary() {
        // Given
        fieldChangeMetadata.addModifiedField("field1");
        fieldChangeMetadata.addModifiedField("field2");
        FieldChangeMetadata childMetadata = FieldChangeMetadata.builder()
                .entityId("child-1")
                .entityType("ChildEntity")
                .build();
        fieldChangeMetadata.addChildChange("child-1", childMetadata);

        // When
        String summary = fieldChangeMetadata.getChangeSummary();

        // Then
        assertThat(summary).isNotBlank();
        assertThat(summary).contains("2"); // field count
        assertThat(summary).contains("1"); // child count
    }
}
