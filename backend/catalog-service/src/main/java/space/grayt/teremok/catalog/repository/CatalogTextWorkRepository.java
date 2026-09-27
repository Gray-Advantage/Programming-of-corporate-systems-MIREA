package space.grayt.teremok.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import space.grayt.teremok.catalog.domain.CatalogTextWork;

public interface CatalogTextWorkRepository {

    void save(CatalogTextWork textWork);

    List<CatalogTextWork> findAll();

    Optional<CatalogTextWork> findById(UUID id);

    long count();
}
