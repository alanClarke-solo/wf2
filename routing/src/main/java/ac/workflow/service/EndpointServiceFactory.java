package ac.workflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EndpointServiceFactory {
    
    private final Map<String, EndpointService> endpointServices;
    
    @Autowired
    public EndpointServiceFactory(List<EndpointService> endpointServices) {
        this.endpointServices = endpointServices.stream()
            .collect(Collectors.toMap(EndpointService::getEndpointType, Function.identity()));
    }
    
    public EndpointService getEndpointService(String endpointType) {
        EndpointService service = endpointServices.get(endpointType);
        if (service == null) {
            throw new IllegalArgumentException("Unsupported endpoint type: " + endpointType);
        }
        return service;
    }
}
