package ambient_invisible_intelligence;

import org.springframework.boot.SpringApplication;

// Convenience launcher for local development: starts the real application together
// with a Testcontainers-managed Postgres, so you can run the server without installing
// Postgres or Docker Compose manually. Run this main() from your IDE.
public class TestApplication {
	public static void main(String[] args) {
		SpringApplication
				.from(Application::main)
				.with(TestContainersConfiguration.class)
				.run(args);
	}
}
