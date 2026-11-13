package space.grayt.teremok.catalog.service;

import java.util.List;
import java.util.UUID;
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

@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class CatalogQueryService {

  private final CatalogTextWorkRepository repository;

  public CatalogQueryService(CatalogTextWorkRepository repository) {
    this.repository = repository;
  }

  public CatalogTextWorkResponse findById(UUID id) {
    return repository
      .findById(id)
      .map(CatalogQueryService::toResponse)
      .orElseThrow(() ->
        new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND,
          "Произведение не найдено"
        )
      );
  }

  public CountResponse count() {
    return new CountResponse(repository.count());
  }

  public List<CatalogTextWorkResponse> findAll() {
    return repository
      .findAll()
      .stream()
      .map(CatalogQueryService::toResponse)
      .toList();
  }

  private static CatalogTextWorkResponse toResponse(CatalogTextWork textWork) {
    var sourceOrigin = textWork.origin();
    var origin = new TextWorkOrigin(
      OriginType.valueOf(sourceOrigin.type().name()),
      sourceOrigin.translatedFrom(),
      sourceOrigin.translators()
    );
    return new CatalogTextWorkResponse(
      textWork.id(),
      textWork.authors(),
      textWork.name(),
      textWork.publicationDate(),
      textWork.language(),
      origin,
      textWork.segmentsCount(),
      SegmentType.valueOf(textWork.segmentType().name())
    );
  }
}
