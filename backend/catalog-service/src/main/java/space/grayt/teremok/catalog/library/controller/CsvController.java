package space.grayt.teremok.catalog.library.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import space.grayt.teremok.catalog.library.filter.WorkFilter;
import space.grayt.teremok.catalog.library.service.CsvService;

@RestController
@RequestMapping("/api/v1/library/works")
public class CsvController {

  private final CsvService csv;

  public CsvController(CsvService csv) {
    this.csv = csv;
  }

  @PostMapping("/import/preview")
  public Object preview(
    @RequestParam MultipartFile file,
    @RequestParam(defaultValue = "NEW") String mode,
    Authentication auth
  ) throws IOException {
    return csv.preview(
      file.getBytes(),
      mode,
      space.grayt.teremok.catalog.library.security.RequestIdentity.actor(auth)
    );
  }

  @PostMapping("/import/apply")
  public Object apply(
    @RequestBody Apply body,
    Authentication auth,
    HttpServletRequest request
  ) {
    return csv.apply(
      body.id(),
      space.grayt.teremok.catalog.library.security.RequestIdentity.actor(auth),
      space.grayt.teremok.http.ApiErrors.correlation(request)
    );
  }

  @GetMapping(value = "/export", produces = "text/csv")
  public ResponseEntity<byte[]> export(@ModelAttribute WorkFilter filter) {
    return ResponseEntity.ok()
      .header(
        "Content-Disposition",
        "attachment; filename=\"teremok-library.csv\""
      )
      .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
      .body(csv.export(filter));
  }

  public record Apply(UUID id) {}
}
