package ac.workflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableJdbcRepositories(basePackages = "ac.workflow.repository")
@EnableTransactionManagement
public class ApplicationConfig {
    
    // Additional configuration beans if needed
}
