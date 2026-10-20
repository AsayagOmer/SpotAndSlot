package ambient_invisible_intelligence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Shared Testcontainers configuration: spins up a throwaway Postgres for tests and
// wires it into Spring Boot automatically via @ServiceConnection. Import it with
// @Import(TestContainersConfiguration.class) on a test class, or via TestApplication.
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

	@Bean
	@ServiceConnection
	public PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
	}
}
