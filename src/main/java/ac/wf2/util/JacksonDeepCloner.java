package ac.wf2.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JacksonDeepCloner {
    
    private final ObjectMapper objectMapper;
    
    public JacksonDeepCloner() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.findAndRegisterModules();
    }
    
    @SuppressWarnings("unchecked")
    public <T> T deepClone(T original) {
        if (original == null) {
            return null;
        }
        
        try {
            String json = objectMapper.writeValueAsString(original);
            return (T) objectMapper.readValue(json, original.getClass());
        } catch (Exception e) {
            log.error("Failed to deep clone object of type: {}", original.getClass().getSimpleName(), e);
            throw new RuntimeException("Deep cloning failed", e);
        }
    }
}
