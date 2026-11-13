package space.grayt.teremok.exporter;

import java.io.IOException;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

  private final ExportService service;

  public ExportController(ExportService service) {
    this.service = service;
  }

  @GetMapping(
    value = "/xlsx",
    produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  )
  public ResponseEntity<byte[]> export(
    @RequestParam(required = false) List<String> services,
    Authentication authentication
  ) throws IOException {
    boolean admin = authentication
      .getAuthorities()
      .stream()
      .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    var file = service.export(services, admin);
    return ResponseEntity.ok()
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"teremok-export.xlsx\""
      )
      .header("X-Export-Failed-Tables", Long.toString(file.failedTables()))
      .body(file.content());
  }

  @GetMapping("/statistics")
  public ExportService.Statistics statistics() {
    return service.statistics();
  }
}
