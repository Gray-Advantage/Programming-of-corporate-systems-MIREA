package space.grayt.teremok.content.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.content.domain.TextWorkContent;

/**
 * Text work content in PostgreSQL. Kafka events are replayed on every start, so save must be an
 * upsert: JpaRepository.save merges a text work whose id already exists, children included.
 */
@Repository
@Transactional(readOnly = true)
public class JpaTextWorkContentRepository implements TextWorkContentRepository {

    private final TextWorkContentJpaRepository jpa;

    public JpaTextWorkContentRepository(TextWorkContentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(TextWorkContent textWork) {
        jpa.save(TextWorkContentEntity.from(textWork));
    }

    @Override
    public Optional<TextWorkContent> findById(UUID id) {
        return jpa.findById(id).map(TextWorkContentEntity::toDomain);
    }

    @Override
    public long count() {
        return jpa.count();
    }
}
