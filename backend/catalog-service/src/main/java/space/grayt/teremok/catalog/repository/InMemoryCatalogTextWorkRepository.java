package space.grayt.teremok.catalog.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import space.grayt.teremok.catalog.domain.CatalogTextWork;

@Repository
public class InMemoryCatalogTextWorkRepository implements CatalogTextWorkRepository {

    private final Map<UUID, CatalogTextWork> textWorks = new ConcurrentHashMap<>();

    @Override
    public void save(CatalogTextWork textWork) {
        textWorks.put(textWork.id(), textWork);
    }

    @Override
    public List<CatalogTextWork> findAll() {
        return textWorks.values().stream()
                .sorted(Comparator.comparing(CatalogTextWork::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CatalogTextWork::id))
                .toList();
    }

    @Override
    public Optional<CatalogTextWork> findById(UUID id) {
        return Optional.ofNullable(textWorks.get(id));
    }

    @Override
    public long count() {
        return textWorks.size();
    }
}
