package space.grayt.teremok.catalog.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.catalog.domain.CatalogTextWork;

/**
 * The catalog in PostgreSQL. Kafka events are replayed on every start, so save must be an upsert:
 * JpaRepository.save merges an entity whose id already exists.
 */
@Repository
@Transactional(readOnly = true)
public class JpaCatalogTextWorkRepository implements CatalogTextWorkRepository {

    private static final Comparator<CatalogTextWork> BY_NAME = Comparator
            .comparing(CatalogTextWork::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(CatalogTextWork::id);

    private final CatalogTextWorkJpaRepository jpa;

    public JpaCatalogTextWorkRepository(CatalogTextWorkJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(CatalogTextWork textWork) {
        jpa.save(CatalogTextWorkEntity.from(textWork));
    }

    @Override
    public List<CatalogTextWork> findAll() {
        return jpa.findAll().stream().map(CatalogTextWorkEntity::toDomain).sorted(BY_NAME).toList();
    }

    @Override
    public Optional<CatalogTextWork> findById(UUID id) {
        return jpa.findById(id).map(CatalogTextWorkEntity::toDomain);
    }

    @Override
    public long count() {
        return jpa.count();
    }
}
