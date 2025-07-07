package ac.workflow.testconfig;

import ac.workflow.repository.custom.OptimizedAggregateUpdateRepository;
import ac.workflow.service.monitoring.AggregateChangeDetectorService;
import ac.workflow.service.monitoring.FieldChangeDetectorService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public OptimizedAggregateUpdateRepository mockOptimizedAggregateUpdateRepository() {
        return Mockito.mock(OptimizedAggregateUpdateRepository.class);
    }

    @Bean
    @Primary
    public AggregateChangeDetectorService mockAggregateChangeDetectorService() {
        return Mockito.mock(AggregateChangeDetectorService.class);
    }

    @Bean
    @Primary
    public FieldChangeDetectorService mockFieldChangeDetectorService() {
        return Mockito.mock(FieldChangeDetectorService.class);
    }
}
