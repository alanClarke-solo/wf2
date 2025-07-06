package ac.workflow.util;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Component
@Log4j2
public class KryoDeepCloner {
    
    private final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(true);
        
        // Register common classes for better performance
        kryo.register(Instant.class);
        kryo.register(HashSet.class);
        kryo.register(Set.class);
        
        return kryo;
    });
    
    @SuppressWarnings("unchecked")
    public <T> T deepClone(T original) {
        if (original == null) {
            return null;
        }
        
        try {
            Kryo kryo = kryoThreadLocal.get();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Output output = new Output(baos);
            
            kryo.writeObject(output, original);
            output.close();
            
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            Input input = new Input(bais);
            
            T cloned = (T) kryo.readObject(input, original.getClass());
            input.close();
            
            return cloned;
        } catch (Exception e) {
            log.error("Failed to deep clone object of type: {}", original.getClass().getSimpleName(), e);
            throw new RuntimeException("Deep cloning failed", e);
        }
    }
}
