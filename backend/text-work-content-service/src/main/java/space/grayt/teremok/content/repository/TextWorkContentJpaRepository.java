package space.grayt.teremok.content.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation: save, findById, count and the rest. */
public interface TextWorkContentJpaRepository extends JpaRepository<TextWorkContentEntity, UUID> {
}
