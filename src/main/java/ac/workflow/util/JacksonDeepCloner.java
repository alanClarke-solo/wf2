package ac.workflow.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utility class for deep cloning objects using Jackson JSON serialization.
 *
 * This class provides robust deep cloning capabilities by serializing objects
 * to JSON and then deserializing them back to create completely independent
 * copies. It includes optimizations for performance and comprehensive error handling.
 *
 * Key features:
 * - Thread-safe operation
 * - Caching for serializability checks
 * - Support for Java 8+ time types
 * - Comprehensive error handling
 * - Performance optimizations
 *
 * @author Workflow Team
 * @version 1.0
 */
@Component
@Log4j2
public class JacksonDeepCloner {

    /**
     * Pre-configured ObjectMapper instance for JSON operations.
     * Configured with appropriate modules and settings for robust cloning.
     */
    private final ObjectMapper objectMapper;

    /**
     * Cache for serializability checks to improve performance.
     * Key: Class, Value: Boolean indicating if the class is serializable
     */
    private final ConcurrentMap<Class<?>, Boolean> serializableCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new JacksonDeepCloner with optimized configuration.
     *
     * The ObjectMapper is configured with:
     * - Java Time module for modern date/time types
     * - Lenient deserialization settings
     * - Proper date formatting
     * - All available modules auto-discovery
     */
    public JacksonDeepCloner() {
        this.objectMapper = new ObjectMapper();

        // Register Java Time module for Java 8+ date/time types
        this.objectMapper.registerModule(new JavaTimeModule());

        // Auto-discover and register all available modules
        this.objectMapper.findAndRegisterModules();

        // Configure for better reliability and compatibility
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);

        log.debug("JacksonDeepCloner initialized with optimized configuration");
    }

    /**
     * Creates a deep copy of the provided object.
     *
     * This method performs deep cloning by serializing the object to JSON
     * and then deserializing it back to create a completely independent copy.
     * All nested objects and collections are also deeply cloned.
     *
     * @param <T> the type of object to clone
     * @param original the original object to clone
     * @return a deep copy of the original object, or null if original is null
     * @throws RuntimeException if cloning fails
     */
    @SuppressWarnings("unchecked")
    public <T> T deepClone(T original) {
        if (original == null) {
            return null;
        }

        Class<?> clazz = original.getClass();

        // Check if the class is likely to be serializable
        if (!isLikelySerializable(clazz)) {
            log.warn("Class {} might not be serializable for deep cloning", clazz.getSimpleName());
        }

        try {
            // Serialize to JSON
            String json = objectMapper.writeValueAsString(original);

            // Deserialize back to object
            T cloned = (T) objectMapper.readValue(json, clazz);

            log.debug("Successfully deep cloned object of type: {}", clazz.getSimpleName());
            return cloned;

        } catch (Exception e) {
            log.error("Failed to deep clone object of type: {}", clazz.getSimpleName(), e);
            throw new RuntimeException("Deep cloning failed for type: " + clazz.getSimpleName(), e);
        }
    }

    /**
     * Checks if an object can be successfully cloned.
     *
     * This method performs a test serialization to determine if the object
     * can be cloned without throwing an exception. Useful for validation
     * before attempting actual cloning operations.
     *
     * @param <T> the type of object to test
     * @param original the object to test for cloning capability
     * @return true if the object can be cloned, false otherwise
     */
    public <T> boolean canClone(T original) {
        if (original == null) {
            return true;
        }

        try {
            // Test serialization only
            objectMapper.writeValueAsString(original);
            return true;
        } catch (Exception e) {
            log.debug("Cannot clone object of type: {}, reason: {}",
                    original.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * Clones an object with additional type safety validation.
     *
     * This method performs the same cloning operation as deepClone but
     * includes additional validation to ensure type safety and proper
     * object construction.
     *
     * @param <T> the type of object to clone
     * @param original the original object to clone
     * @param expectedType the expected type of the cloned object
     * @return a deep copy of the original object
     * @throws RuntimeException if cloning fails or type validation fails
     */
    @SuppressWarnings("unchecked")
    public <T> T deepCloneWithTypeValidation(T original, Class<T> expectedType) {
        if (original == null) {
            return null;
        }

        if (!expectedType.isAssignableFrom(original.getClass())) {
            throw new IllegalArgumentException(
                    String.format("Original object type %s is not assignable to expected type %s",
                            original.getClass().getSimpleName(), expectedType.getSimpleName()));
        }

        try {
            String json = objectMapper.writeValueAsString(original);
            T cloned = objectMapper.readValue(json, expectedType);

            // Additional validation
            if (cloned == null) {
                throw new RuntimeException("Cloned object is null");
            }

            if (!expectedType.isAssignableFrom(cloned.getClass())) {
                throw new RuntimeException(
                        String.format("Cloned object type %s is not assignable to expected type %s",
                                cloned.getClass().getSimpleName(), expectedType.getSimpleName()));
            }

            log.debug("Successfully deep cloned object with type validation: {}", expectedType.getSimpleName());
            return cloned;

        } catch (Exception e) {
            log.error("Failed to deep clone object with type validation: {}", expectedType.getSimpleName(), e);
            throw new RuntimeException("Deep cloning with type validation failed", e);
        }
    }

    /**
     * Gets the size of the serializability cache.
     *
     * @return current cache size
     */
    public int getCacheSize() {
        return serializableCache.size();
    }

    /**
     * Clears the serializability cache.
     *
     * This method is useful for memory management in long-running applications
     * or for testing scenarios where cache state needs to be reset.
     */
    public void clearCache() {
        int size = serializableCache.size();
        serializableCache.clear();
        log.debug("Cleared serializability cache, removed {} entries", size);
    }

    /**
     * Checks if a class is likely to be serializable by Jackson.
     *
     * This method uses caching to improve performance and applies heuristics
     * to determine if a class can be successfully serialized to JSON.
     *
     * @param clazz the class to check
     * @return true if the class is likely serializable, false otherwise
     */
    private boolean isLikelySerializable(Class<?> clazz) {
        return serializableCache.computeIfAbsent(clazz, this::checkSerializability);
    }

    /**
     * Performs actual serializability check for a class.
     *
     * This method applies various heuristics to determine if a class
     * is likely to be serializable by Jackson without actually attempting
     * serialization (which would be expensive).
     *
     * @param clazz the class to check
     * @return true if the class appears to be serializable
     */
    private boolean checkSerializability(Class<?> clazz) {
        // Primitive types and their wrappers are always serializable
        if (clazz.isPrimitive()) {
            return true;
        }

        // Enums are serializable
        if (clazz.isEnum()) {
            return true;
        }

        String className = clazz.getName();

        // Common Java types that are known to be serializable
        if (className.startsWith("java.lang") ||
                className.startsWith("java.util") ||
                className.startsWith("java.time") ||
                className.startsWith("java.math") ||
                className.startsWith("java.net")) {
            return true;
        }

        // Arrays are generally serializable if their component type is
        if (clazz.isArray()) {
            return isLikelySerializable(clazz.getComponentType());
        }

        // Classes with Jackson annotations are likely serializable
        if (hasJacksonAnnotations(clazz)) {
            return true;
        }

        // Classes with default constructors are generally serializable
        if (hasDefaultConstructor(clazz)) {
            return true;
        }

        // If none of the above, it might still be serializable but less likely
        return false;
    }

    /**
     * Checks if a class has Jackson-related annotations.
     *
     * @param clazz the class to check
     * @return true if Jackson annotations are present
     */
    private boolean hasJacksonAnnotations(Class<?> clazz) {
        return Arrays.stream(clazz.getAnnotations())
                .anyMatch(annotation -> annotation.annotationType().getPackage().getName()
                        .startsWith("com.fasterxml.jackson"));
    }

    /**
     * Checks if a class has a default (no-argument) constructor.
     *
     * @param clazz the class to check
     * @return true if a default constructor exists
     */
    private boolean hasDefaultConstructor(Class<?> clazz) {
        try {
            clazz.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}