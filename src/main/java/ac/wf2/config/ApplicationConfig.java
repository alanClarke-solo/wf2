package ac.wf2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableJdbcRepositories(basePackages = "ac.wf2.repository")
@EnableTransactionManagement
public class ApplicationConfig {
    
    // Additional configuration beans if needed
}
