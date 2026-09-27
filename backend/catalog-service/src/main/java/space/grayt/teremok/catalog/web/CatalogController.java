package space.grayt.teremok.catalog.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.grayt.teremok.catalog.domain.CatalogTextWork;
import space.grayt.teremok.catalog.repository.CatalogTextWorkRepository;
import space.grayt.teremok.client.contract.CatalogTextWorkResponse;
import space.grayt.teremok.client.contract.CatalogTextWorkResponse.OriginType;
import space.grayt.teremok.client.contract.CatalogTextWorkResponse.SegmentType;
import space.grayt.teremok.client.contract.CatalogTextWorkResponse.TextWorkOrigin;
import space.grayt.teremok.client.contract.CountResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/text-works")
public class CatalogController {

    private final CatalogTextWorkRepository repository;

    public CatalogController(CatalogTextWorkRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CatalogTextWorkResponse> findAll() {
        return repository.findAll().stream().map(CatalogController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatalogTextWorkResponse> findById(@PathVariable UUID id) {
        return repository
                .findById(id)
                .map(CatalogController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/count")
    public CountResponse count() {
        return new CountResponse(repository.count());
    }

    private static CatalogTextWorkResponse toResponse(CatalogTextWork textWork) {
        var sourceOrigin = textWork.origin();
        var origin = new TextWorkOrigin(
                OriginType.valueOf(sourceOrigin.type().name()),
                sourceOrigin.translatedFrom(),
                sourceOrigin.translators());
        return new CatalogTextWorkResponse(
                textWork.id(),
                textWork.authors(),
                textWork.name(),
                textWork.publicationDate(),
                textWork.language(),
                origin,
                textWork.segmentsCount(),
                SegmentType.valueOf(textWork.segmentType().name()));
    }
}
