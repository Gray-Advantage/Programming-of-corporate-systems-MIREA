package space.grayt.teremok.catalog.repository;

import space.grayt.teremok.catalog.domain.CatalogTextWork;

public interface CatalogTextWorkRepository {

    void save(CatalogTextWork textWork);

    long count();
}
