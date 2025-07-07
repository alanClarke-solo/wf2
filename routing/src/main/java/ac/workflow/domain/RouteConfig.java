package ac.workflow.domain;

import java.util.Map;

public class RouteConfig {
    private String routeId;
    private String endpointType; // SOAP, REST, etc.
    private String endpointUrl;
    private String userId;
    private String password;
    private Map<String, Object> properties;
    private int statusThresholdMinutes;
    
    // Constructors
    public RouteConfig() {}
    
    // Getters and Setters
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    
    public String getEndpointType() { return endpointType; }
    public void setEndpointType(String endpointType) { this.endpointType = endpointType; }
    
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    
    public int getStatusThresholdMinutes() { return statusThresholdMinutes; }
    public void setStatusThresholdMinutes(int statusThresholdMinutes) { this.statusThresholdMinutes = statusThresholdMinutes; }
}
