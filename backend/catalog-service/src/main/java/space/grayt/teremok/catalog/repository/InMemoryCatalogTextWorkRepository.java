package space.grayt.teremok.catalog.repository;

import java.util.Map;
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
    public long count() {
        return textWorks.size();
    }
}
