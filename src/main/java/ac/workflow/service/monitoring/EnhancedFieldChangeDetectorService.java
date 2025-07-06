package ac.workflow.service.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
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
