package ac.workflow.service.dag;

import ac.workflow.domain.dto.WorkflowConfigDto;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Log4j2
public class WorkflowDAGService {
    
    public DAG buildDAG(WorkflowConfigDto config) {
        DAG dag = new DAG();
        
        // Add all tasks as nodes
        config.getTasks().forEach(task -> dag.addNode(task.getTaskId()));
        
        // Add dependencies as edges
        if (config.getDependencies() != null) {
            config.getDependencies().forEach(dependency -> {
                dag.addEdge(dependency.getDependsOn(), dependency.getTaskId());
            });
        }
        
        // Validate DAG (no cycles)
        if (hasCycle(dag)) {
            throw new IllegalArgumentException("Workflow configuration contains cycles");
        }
        
        return dag;
    }
    
    public List<String> getTopologicalOrder(DAG dag) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (String node : dag.getNodes()) {
            if (!visited.contains(node)) {
                topologicalSort(dag, node, visited, visiting, result);
            }
        }
        
        Collections.reverse(result);
        return result;
    }
    
    private void topologicalSort(DAG dag, String node, Set<String> visited, 
                                Set<String> visiting, List<String> result) {
        visiting.add(node);
        
        for (String neighbor : dag.getAdjacencyList().getOrDefault(node, Collections.emptyList())) {
            if (visiting.contains(neighbor)) {
                throw new IllegalStateException("Cycle detected in DAG");
            }
            if (!visited.contains(neighbor)) {
                topologicalSort(dag, neighbor, visited, visiting, result);
            }
        }
        
        visiting.remove(node);
        visited.add(node);
        result.add(node);
    }
    
    private boolean hasCycle(DAG dag) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (String node : dag.getNodes()) {
            if (!visited.contains(node)) {
                if (hasCycleDFS(dag, node, visited, visiting)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean hasCycleDFS(DAG dag, String node, Set<String> visited, Set<String> visiting) {
        visiting.add(node);
        
        for (String neighbor : dag.getAdjacencyList().getOrDefault(node, Collections.emptyList())) {
            if (visiting.contains(neighbor)) {
                return true; // Back edge found, cycle detected
            }
            if (!visited.contains(neighbor) && hasCycleDFS(dag, neighbor, visited, visiting)) {
                return true;
            }
        }
        
        visiting.remove(node);
        visited.add(node);
        return false;
    }
    
    public static class DAG {
        private final Set<String> nodes = new HashSet<>();
        private final Map<String, List<String>> adjacencyList = new HashMap<>();
        
        public void addNode(String node) {
            nodes.add(node);
            adjacencyList.putIfAbsent(node, new ArrayList<>());
        }
        
        public void addEdge(String from, String to) {
            adjacencyList.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        }
        
        public Set<String> getNodes() {
            return new HashSet<>(nodes);
        }
        
        public Map<String, List<String>> getAdjacencyList() {
            return new HashMap<>(adjacencyList);
        }
    }
}