package space.grayt.teremok.content.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.grayt.teremok.client.contract.CountResponse;
import space.grayt.teremok.client.contract.TextWorkContentResponse;

@RestController
@RequestMapping("/api/v1/text-work-content/text-works")
public class TextWorkContentController {

  private final space.grayt.teremok.content.service.TextWorkContentQueryService service;

  public TextWorkContentController(
    space.grayt.teremok.content.service.TextWorkContentQueryService service
  ) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public ResponseEntity<TextWorkContentResponse> findById(
    @PathVariable UUID id
  ) {
    return ResponseEntity.ok(service.findById(id));
  }

  @GetMapping("/count")
  public CountResponse count() {
    return service.count();
  }
}
