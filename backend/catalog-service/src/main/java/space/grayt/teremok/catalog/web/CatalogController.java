package space.grayt.teremok.catalog.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.grayt.teremok.catalog.repository.CatalogTextWorkRepository;

@RestController
@RequestMapping("/api/v1/catalog/text-works")
public class CatalogController {

    private final CatalogTextWorkRepository repository;

    public CatalogController(CatalogTextWorkRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/count")
    public CountResponse count() {
        return new CountResponse(repository.count());
    }

    public record CountResponse(long count) {
    }
}
