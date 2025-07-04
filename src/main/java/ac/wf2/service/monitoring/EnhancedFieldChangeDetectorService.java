package ac.wf2.service.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EnhancedFieldChangeDetectorService {
    
    private final ObjectMapper objectMapper;
    
    public EnhancedFieldChangeDetectorService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @SuppressWarnings("unchecked")
    private <T> T deepClone(T original) {
        try {
            // Using Jackson for deep cloning
            String json = objectMapper.writeValueAsString(original);
            return (T) objectMapper.readValue(json, original.getClass());
        } catch (Exception e) {
            log.error("Failed to deep clone object", e);
            throw new RuntimeException("Deep cloning failed", e);
        }
    }
    
    // Rest of the implementation...
}
