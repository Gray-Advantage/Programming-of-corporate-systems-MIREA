package space.grayt.teremok.catalog.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data generates the implementation: save, findAll, findById, count and the rest. */
public interface CatalogTextWorkJpaRepository extends JpaRepository<CatalogTextWorkEntity, UUID> {
}
