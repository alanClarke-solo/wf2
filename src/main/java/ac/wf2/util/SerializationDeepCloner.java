package ac.wf2.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;

@Component
@Slf4j
public class SerializationDeepCloner {
    
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T deepClone(T original) {
        if (original == null) {
            return null;
        }
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(original);
            oos.close();
            
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            T cloned = (T) ois.readObject();
            ois.close();
            
            return cloned;
        } catch (Exception e) {
            log.error("Failed to deep clone object of type: {}", original.getClass().getSimpleName(), e);
            throw new RuntimeException("Deep cloning failed", e);
        }
    }
}
