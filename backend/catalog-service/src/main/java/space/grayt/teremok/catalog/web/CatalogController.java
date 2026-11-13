package space.grayt.teremok.catalog.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.grayt.teremok.client.contract.CatalogTextWorkResponse;
import space.grayt.teremok.client.contract.CountResponse;

@RestController
@RequestMapping("/api/v1/catalog/text-works")
public class CatalogController {

  private final space.grayt.teremok.catalog.service.CatalogQueryService service;

  public CatalogController(
    space.grayt.teremok.catalog.service.CatalogQueryService service
  ) {
    this.service = service;
  }

  @GetMapping
  public List<CatalogTextWorkResponse> findAll() {
    return service.findAll();
  }

  @GetMapping("/{id}")
  public ResponseEntity<CatalogTextWorkResponse> findById(
    @PathVariable UUID id
  ) {
    return ResponseEntity.ok(service.findById(id));
  }

  @GetMapping("/count")
  public CountResponse count() {
    return service.count();
  }
}
