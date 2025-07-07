
package ac.workflow.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JacksonDeepClonerTest {

    private JacksonDeepCloner deepCloner;

    @BeforeEach
    void setUp() {
        deepCloner = new JacksonDeepCloner();
    }

    @Test
    void deepClone_ShouldReturnNull_WhenOriginalIsNull() {
        // When
        Object result = deepCloner.deepClone(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void deepClone_ShouldCreateIndependentCopy_ForSimpleObject() {
        // Given
        TestObject original = new TestObject("test", 42, OffsetDateTime.now());

        // When
        TestObject cloned = deepCloner.deepClone(original);

        // Then
        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getName()).isEqualTo(original.getName());
        assertThat(cloned.getValue()).isEqualTo(original.getValue());
        assertThat(cloned.getTimestamp()).isEqualTo(original.getTimestamp());
    }

    @Test
    void deepClone_ShouldCreateIndependentCopy_ForComplexObject() {
        // Given
        ComplexTestObject original = createComplexTestObject();

        // When
        ComplexTestObject cloned = deepCloner.deepClone(original);

        // Then
        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getList()).isNotSameAs(original.getList());
        assertThat(cloned.getMap()).isNotSameAs(original.getMap());
        assertThat(cloned.getList()).containsExactlyElementsOf(original.getList());
        assertThat(cloned.getMap()).containsExactlyInAnyOrderEntriesOf(original.getMap());
    }

    @Test
    void deepClone_ShouldHandleModifications_WithoutAffectingOriginal() {
        // Given
        ComplexTestObject original = createComplexTestObject();
        ComplexTestObject cloned = deepCloner.deepClone(original);

        // When
        cloned.getList().add("modified");
        cloned.getMap().put("newKey", "newValue");

        // Then
        assertThat(original.getList()).doesNotContain("modified");
        assertThat(original.getMap()).doesNotContainKey("newKey");
        assertThat(cloned.getList()).contains("modified");
        assertThat(cloned.getMap()).containsKey("newKey");
    }

    @Test
    void deepClone_ShouldThrowException_ForNonSerializableObject() {
        // Given
        NonSerializableObject original = new NonSerializableObject();

        // When/Then
        assertThatThrownBy(() -> deepCloner.deepClone(original))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Deep cloning failed");
    }

    @Test
    void canClone_ShouldReturnTrue_ForSerializableObject() {
        // Given
        TestObject testObject = new TestObject("test", 42, OffsetDateTime.now());

        // When
        boolean result = deepCloner.canClone(testObject);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void canClone_ShouldReturnFalse_ForNonSerializableObject() {
        // Given
        NonSerializableObject nonSerializableObject = new NonSerializableObject();

        // When
        boolean result = deepCloner.canClone(nonSerializableObject);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void deepCloneWithTypeValidation_ShouldReturnCorrectType() {
        // Given
        TestObject original = new TestObject("test", 42, OffsetDateTime.now());

        // When
        TestObject cloned = deepCloner.deepCloneWithTypeValidation(original, TestObject.class);

        // Then
        assertThat(cloned).isInstanceOf(TestObject.class);
        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getName()).isEqualTo(original.getName());
    }

    @Test
    void getCacheSize_ShouldReturnZero_Initially() {
        // When
        int cacheSize = deepCloner.getCacheSize();

        // Then
        assertThat(cacheSize).isZero();
    }

    @Test
    void clearCache_ShouldResetCacheSize() {
        // Given
        TestObject testObject = new TestObject("test", 42, OffsetDateTime.now());
        deepCloner.canClone(testObject); // This should populate cache

        // When
        deepCloner.clearCache();

        // Then
        assertThat(deepCloner.getCacheSize()).isZero();
    }

    // Test helper classes - Made static and public for proper Jackson serialization
    public static class TestObject {
        private String name;
        private int value;
        private OffsetDateTime timestamp;

        // Default constructor required for Jackson
        public TestObject() {}

        public TestObject(String name, int value, OffsetDateTime timestamp) {
            this.name = name;
            this.value = value;
            this.timestamp = timestamp;
        }

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public OffsetDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class ComplexTestObject {
        private List<String> list;
        private Map<String, String> map;

        // Default constructor required for Jackson
        public ComplexTestObject() {}

        public ComplexTestObject(List<String> list, Map<String, String> map) {
            this.list = list;
            this.map = map;
        }

        public List<String> getList() { return list; }
        public void setList(List<String> list) { this.list = list; }
        public Map<String, String> getMap() { return map; }
        public void setMap(Map<String, String> map) { this.map = map; }
    }

    public static class NonSerializableObject {
        // Use a field that Jackson definitely cannot serialize
        private final Object nonSerializableField = new Object() {
            private Thread thread = new Thread();

            // Force Jackson to access this field during serialization
            @JsonProperty
            public Thread getThread() {
                return thread;
            }
        };

        @JsonProperty
        public Object getNonSerializableField() {
            return nonSerializableField;
        }
    }

    private ComplexTestObject createComplexTestObject() {
        List<String> list = new ArrayList<>();
        list.add("item1");
        list.add("item2");

        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        return new ComplexTestObject(list, map);
    }
}