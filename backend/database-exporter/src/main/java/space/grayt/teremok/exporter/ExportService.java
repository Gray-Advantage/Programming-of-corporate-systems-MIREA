package space.grayt.teremok.exporter;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExportService {

  private static final Logger LOG = LoggerFactory.getLogger(
    ExportService.class
  );
  private static final List<MetricQuery> QUERIES = List.of(
    new MetricQuery(
      "auth-service",
      "Зарегистрировано пользователей",
      "SELECT COUNT(*) FROM user_account"
    ),
    new MetricQuery(
      "catalog-service",
      "Добавлено произведений",
      "SELECT COUNT(*) FROM catalog_text_work"
    ),
    new MetricQuery(
      "draft-recordings-service",
      "Черновых вариантов записи",
      "SELECT COUNT(*) FROM draft_fragment_recording"
    ),
    new MetricQuery(
      "recordings-service",
      "Опубликовано озвучек ролей",
      "SELECT COUNT(*) FROM role_recording"
    ),
    new MetricQuery(
      "recordings-service",
      "Готовых аудиосборок",
      "SELECT COUNT(*) FROM render_job WHERE status = 'COMPLETED'"
    )
  );

  public ExportFile export(List<String> services, boolean admin)
    throws IOException {
    var names =
      services == null
        ? DatabaseExporterApplication.targets()
            .stream()
            .map(DatabaseTarget::serviceName)
            .collect(java.util.stream.Collectors.toSet())
        : new HashSet<>(services);
    var targets = DatabaseExporterApplication.targets()
      .stream()
      .filter(t -> names.contains(t.serviceName()))
      .toList();
    if (targets.isEmpty() || targets.size() != names.size()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Выберите существующие сервисы для выгрузки"
      );
    }
    var path = Files.createTempFile("teremok-export-", ".xlsx");
    try {
      var report = new DatabaseExporter().export(targets, path, table ->
        ExportAccess.canExport(table, admin)
      );
      return new ExportFile(Files.readAllBytes(path), report.failedTables());
    } finally {
      Files.deleteIfExists(path);
    }
  }

  public Statistics statistics() {
    var targets = DatabaseExporterApplication.targets();
    var metrics = new ArrayList<Metric>();
    for (var query : QUERIES) {
      var target = targets
        .stream()
        .filter(t -> t.serviceName().equals(query.service()))
        .findFirst()
        .orElseThrow();
      try (
        var connection = DriverManager.getConnection(
          target.jdbcUrl(),
          target.username(),
          target.password()
        )
      ) {
        connection.setReadOnly(true);
        try (var statement = connection.createStatement()) {
          statement.setQueryTimeout(5);
          try (var result = statement.executeQuery(query.sql())) {
            result.next();
            metrics.add(new Metric(query.title(), result.getLong(1), null));
          }
        }
      } catch (Exception exception) {
        LOG.warn("Statistics unavailable for {}", query.service(), exception);
        metrics.add(
          new Metric(query.title(), null, "Сервис временно недоступен")
        );
      }
    }
    return new Statistics(Instant.now(), metrics);
  }

  private record MetricQuery(String service, String title, String sql) {}

  public record Metric(String title, Long value, String error) {}

  public record Statistics(Instant updatedAt, List<Metric> metrics) {}

  public record ExportFile(byte[] content, long failedTables) {}
}
