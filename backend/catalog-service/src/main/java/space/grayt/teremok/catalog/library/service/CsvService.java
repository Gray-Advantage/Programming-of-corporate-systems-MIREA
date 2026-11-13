package space.grayt.teremok.catalog.library.service;

import jakarta.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import space.grayt.teremok.catalog.library.dto.*;
import space.grayt.teremok.catalog.library.filter.WorkFilter;
import space.grayt.teremok.catalog.library.repository.Works;
import space.grayt.teremok.catalog.library.security.LibrarySecurity.Actor;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CsvService {

  public static final String[] HEADERS = {
    "code",
    "title",
    "author",
    "description",
    "language",
    "collectionId",
    "status",
    "targetMinutes",
    "featured",
    "tags",
    "script",
  };
  public static final String[] EXPORT_HEADERS = java.util.stream.Stream.concat(
    Arrays.stream(HEADERS),
    java.util.stream.Stream.of("id", "originType", "rolesCount", "complexity", "archived", "createdAt", "updatedAt", "version", "sourcePayload")
  ).toArray(String[]::new);
  private final JdbcTemplate db;
  private final Works works;
  private final LibraryService library;
  private final Validator validator;
  private final JsonMapper json = JsonMapper.builder().build();

  public CsvService(
    JdbcTemplate db,
    Works works,
    LibraryService library,
    Validator validator
  ) {
    this.db = db;
    this.works = works;
    this.library = library;
    this.validator = validator;
  }

  @Transactional
  public Object preview(byte[] data, String mode, Actor actor) {
    if (data.length > 5 * 1024 * 1024) bad("Файл не должен превышать 5 МБ");
    if (!Set.of("NEW", "UPSERT").contains(mode)) bad("Выберите режим импорта");
    var valid = new ArrayList<WorkInput>();
    var errors = new ArrayList<Map<String, Object>>();
    var seen = new HashSet<String>();
    String text = new String(data, StandardCharsets.UTF_8).replaceFirst(
      "^\\uFEFF",
      ""
    );
    try (
      var parser = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true)
        .get()
        .parse(new StringReader(text))
    ) {
      if (!parser.getHeaderMap().keySet().containsAll(List.of(HEADERS))) bad(
        "Неверные заголовки CSV. Скачайте шаблон"
      );
      for (var row : parser) {
        if (row.getRecordNumber() > 1000) bad("Не более 1000 строк за импорт");
        try {
          String code = row.get("code").trim();
          if (!seen.add(code)) throw new IllegalArgumentException(
            "Повтор кода в файле"
          );
          var existing = works.findByCode(code);
          if (
            "NEW".equals(mode) && existing.isPresent()
          ) throw new IllegalArgumentException("Код уже существует");
          if (
            !Set.of("true", "false").contains(row.get("featured"))
          ) throw new IllegalArgumentException(
            "featured должен быть true или false"
          );
          var tags = new LinkedHashSet<String>();
          for (var tag : row.get("tags").split(";"))
            if (!tag.isBlank()) tags.add(tag.trim());
          var input = new WorkInput(
            code,
            row.get("title"),
            row.get("author"),
            row.get("description"),
            row.get("language"),
            UUID.fromString(row.get("collectionId")),
            row.get("status"),
            Integer.parseInt(row.get("targetMinutes")),
            Boolean.parseBoolean(row.get("featured")),
            tags,
            row.get("script"),
            existing.map(w -> w.version).orElse(0L)
          );
          if (
            !validator.validate(input).isEmpty()
          ) throw new IllegalArgumentException(
            "Проверьте обязательные поля, длины и диапазоны"
          );
          if (
            db.queryForObject(
              "SELECT count(*) FROM library_collection WHERE id=? AND active",
              Integer.class,
              input.collectionId()
            ) == 0
          ) throw new IllegalArgumentException("Активная коллекция не найдена");
          if (
            existing.isPresent() && existing.get().archived
          ) throw new IllegalArgumentException("Запись находится в архиве");
          if (
            existing.isPresent() &&
            !existing.get().script.isBlank() &&
            !existing.get().script.equals(input.script())
          ) throw new IllegalArgumentException(
            "Нельзя заменить существующий сценарий"
          );
          valid.add(input);
        } catch (IllegalArgumentException e) {
          errors.add(
            Map.of(
              "row",
              row.getRecordNumber() + 1,
              "message",
              Objects.toString(e.getMessage(), "Неверная строка")
            )
          );
        }
      }
    } catch (IOException | UncheckedIOException e) {
      bad("Не удалось прочитать CSV");
    }
    UUID id = UUID.randomUUID();
    db.update(
      "INSERT INTO library_import VALUES(?,?,?,?,now()+interval '30 minutes',false)",
      id,
      actor.id(),
      mode,
      json.writeValueAsString(errors.isEmpty() ? valid : List.of())
    );
    return Map.of(
      "id",
      id,
      "validCount",
      valid.size(),
      "errorCount",
      errors.size(),
      "errors",
      errors,
      "canApply",
      !valid.isEmpty() && errors.isEmpty()
    );
  }

  @Transactional
  public Object apply(UUID id, Actor actor, String correlation) {
    var rows = db.queryForList(
      "SELECT * FROM library_import WHERE id=? AND user_id=? AND expires_at>now() AND NOT applied FOR UPDATE",
      id,
      actor.id()
    );
    if (rows.isEmpty()) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Проверка истекла или импорт уже применён"
    );
    var row = rows.getFirst();
    var inputs = json.readValue(
      row.get("payload").toString(),
      WorkInput[].class
    );
    if (inputs.length == 0) bad("В файле нет корректных строк");
    for (var input : inputs) {
      var found = works.findByCode(input.code());
      if (
        found.isPresent() && "NEW".equals(row.get("mode"))
      ) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Код появился после проверки"
      );
      library.save(
        found.map(w -> w.id).orElse(null),
        input,
        actor,
        correlation
      );
    }
    db.update("UPDATE library_import SET applied=true WHERE id=?", id);
    return Map.of("imported", inputs.length);
  }

  @Transactional(readOnly = true)
  public byte[] export(WorkFilter filter) {
    var records = works.findAll(
      filter.specification(),
      org.springframework.data.domain.Sort.by("title")
    );
    if (records.size() > 10000) bad(
      "Уточните фильтры: экспорт до 10000 записей"
    );
    try (
      var out = new StringWriter();
      var printer = new CSVPrinter(
        out,
        CSVFormat.DEFAULT.builder().setHeader(EXPORT_HEADERS).get()
      )
    ) {
      for (var w : records)
        printer.printRecord(
          w.code,
          w.title,
          w.author,
          w.description,
          w.language,
          w.collectionId,
          w.status,
          w.targetMinutes,
          w.featured,
          String.join(";", w.tags),
          w.script,
          w.id, w.originType, w.rolesCount, w.complexity, w.archived,
          w.createdAt, w.updatedAt, w.version, w.sourcePayload
        );
      printer.flush();
      return ("\uFEFF" + out).getBytes(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void bad(String message) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
