package ambient_invisible_intelligence.bdd;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import ambient_invisible_intelligence.TestContainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;

// Boots the full application (with a Testcontainers-managed Postgres) once for
// all Cucumber scenarios; step definitions are Spring beans wired against it.
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
public class CucumberSpringConfiguration {
}
