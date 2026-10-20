package ambient_invisible_intelligence.data;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandRepository extends JpaRepository<CommandEntity, String> {
}
