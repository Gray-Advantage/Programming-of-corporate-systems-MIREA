package space.grayt.teremok.catalog.library.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import space.grayt.teremok.catalog.library.domain.LibraryWork;
import space.grayt.teremok.catalog.library.dto.*;
import space.grayt.teremok.catalog.library.repository.Works;
import space.grayt.teremok.catalog.library.security.LibrarySecurity.Actor;
import space.grayt.teremok.events.TextWorkAddedEvent;
import space.grayt.teremok.events.TextWorkAddedEvent.*;
import tools.jackson.databind.json.JsonMapper;

@Service
public class LibraryService {

  public static final UUID DEFAULT_COLLECTION = UUID.fromString(
    "10000000-0000-0000-0000-000000000099"
  );
  private final Works works;
  private final JdbcTemplate db;
  private final JsonMapper json = JsonMapper.builder().build();

  public LibraryService(Works works, JdbcTemplate db) {
    this.works = works;
    this.db = db;
  }

  public LibraryWork required(UUID id) {
    return works
      .findById(id)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Произведение не найдено"
        )
      );
  }

  public static String complexity(int minutes, int roles) {
    return minutes >= 30 || roles >= 6
      ? "HIGH"
      : minutes >= 10 || roles >= 3
        ? "MEDIUM"
        : "LOW";
  }

  @Transactional
  public WorkView save(
    UUID id,
    WorkInput input,
    Actor actor,
    String correlation
  ) {
    if (
      !actor.active() || actor.mustChangePassword() ||
      (!actor.roles().contains("EDITOR") && !actor.roles().contains("ADMIN"))
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Нужны права редактора"
    );
    var w = id == null ? new LibraryWork() : required(id);
    if (
      id != null && w.version != input.version()
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Произведение уже изменено. Обновите страницу"
    );
    if (w.archived) bad("Сначала восстановите произведение из архива");
    if (
      db.queryForObject(
        "SELECT count(*) FROM library_collection WHERE id=? AND (active OR id=?)",
        Integer.class,
        input.collectionId(),
        w.collectionId == null ? new UUID(0, 0) : w.collectionId
      ) == 0
    ) bad("Выберите активную коллекцию");
    if (id == null) {
      w.id = UUID.randomUUID();
      w.createdAt = Instant.now();
    }
    if (
      w.sourcePayload != null &&
      !w.script.isBlank() &&
      input.script() != null &&
      !input.script().equals(w.script)
    ) bad(
      "Текст с существующими ролями неизменяем. Создайте новое произведение для другого сценария"
    );
    w.code = input.code().trim();
    w.title = input.title().trim();
    w.author = input.author().trim();
    w.description = input.description().trim();
    w.language = input.language();
    w.collectionId = input.collectionId();
    w.status = input.status();
    w.targetMinutes = input.targetMinutes();
    w.featured = input.featured();
    w.tags = new LinkedHashSet<>(input.tags());
    w.updatedAt = Instant.now();
    var payload = payload(w, input.script());
    w.rolesCount = payload.voiceParts().size();
    w.complexity = complexity(w.targetMinutes, w.rolesCount);
    w.sourcePayload = json.writeValueAsString(payload);
    w = works.saveAndFlush(w);
    enqueue(payload);
    audit(
      actor,
      id == null ? "WORK_CREATED" : "WORK_UPDATED",
      w.id,
      w.title,
      correlation
    );
    return WorkView.from(w);
  }

  private TextWorkPayload payload(LibraryWork w, String script) {
    if (w.sourcePayload != null && !w.script.isBlank()) {
      var source = json.readValue(w.sourcePayload, TextWorkPayload.class);
      return new TextWorkPayload(
        w.id,
        List.of(w.author),
        w.title,
        source.publicationDate(),
        w.language,
        source.origin(),
        source.segmentType(),
        source.segments(),
        source.voiceParts()
      );
    }
    w.script = Objects.toString(script, "").trim();
    var roleIds = new LinkedHashMap<String, UUID>();
    var counts = new HashMap<UUID, Integer>();
    var fragments = new ArrayList<VoicePartFragmentPayload>();
    int order = 0;
    for (var line : w.script.split("\\R")) {
      if (line.isBlank()) continue;
      int colon = line.indexOf(':');
      String name = colon > 0 ? line.substring(0, colon).trim() : "Рассказчик";
      String text = colon > 0 ? line.substring(colon + 1).trim() : line.trim();
      if (name.length() > 160 || text.isBlank()) bad(
        "Проверьте строки сценария: Роль: реплика"
      );
      UUID roleId = roleIds.computeIfAbsent(name, k -> UUID.randomUUID());
      counts.merge(roleId, 1, Integer::sum);
      fragments.add(
        new VoicePartFragmentPayload(UUID.randomUUID(), ++order, text, roleId)
      );
    }
    var roles = roleIds
      .entrySet()
      .stream()
      .map(e ->
        new VoicePartPayload(e.getValue(), e.getKey(), counts.get(e.getValue()))
      )
      .toList();
    return new TextWorkPayload(
      w.id,
      List.of(w.author),
      w.title,
      null,
      w.language,
      new TextWorkOrigin(OriginType.ORIGINAL, null, List.of()),
      SegmentType.SINGLE_SEGMENT,
      List.of(new SegmentPayload(UUID.randomUUID(), 1, w.title, fragments)),
      roles
    );
  }

  public void enqueue(TextWorkPayload payload) {
    db.update(
      "INSERT INTO library_outbox VALUES(?,?,now())",
      UUID.randomUUID(),
      json.writeValueAsString(
        new TextWorkAddedEvent(UUID.randomUUID(), 1, Instant.now(), payload)
      )
    );
  }

  @Transactional
  public void ingest(TextWorkAddedEvent event) {
    if (
      works.existsById(event.textWork().id()) ||
      isDeleted(event.textWork().id())
    ) return;
    var source = event.textWork();
    var w = new LibraryWork();
    w.id = source.id();
    w.code = "TW-" + source.id().toString().substring(0, 8);
    w.title = source.name();
    w.author = String.join(", ", source.authors());
    w.collectionId = DEFAULT_COLLECTION;
    w.status = "RECORDING";
    w.language = source.language();
    w.originType = source.origin().type().name();
    w.targetMinutes = Math.max(
      1,
      (int) Math.ceil(
        source
          .segments()
          .stream()
          .flatMap(x -> x.fragments().stream())
          .mapToInt(x -> x.content().length())
          .sum() / 700.0
      )
    );
    w.rolesCount = source.voiceParts().size();
    w.complexity = complexity(w.targetMinutes, w.rolesCount);
    w.createdAt = Instant.now();
    w.updatedAt = w.createdAt;
    w.sourcePayload = json.writeValueAsString(source);
    var names = new HashMap<UUID, String>();
    source.voiceParts().forEach(r -> names.put(r.id(), r.name()));
    w.script = source
      .segments()
      .stream()
      .flatMap(s -> s.fragments().stream())
      .map(f -> names.get(f.voicePartId()) + ": " + f.content())
      .collect(java.util.stream.Collectors.joining("\n"));
    works.save(w);
  }

  @Transactional
  public void operation(
    UUID id,
    String operation,
    String value,
    Long version,
    Actor actor,
    String correlation
  ) {
    if (!actor.active() || actor.mustChangePassword() ||
        (!actor.roles().contains("EDITOR") && !actor.roles().contains("ADMIN")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нужны права редактора");
    var w = required(id);
    if (
      version != null && w.version != version
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Версия записи изменилась"
    );
    switch (operation) {
      case "archive" -> w.archived = true;
      case "restore" -> w.archived = false;
      case "status" -> {
        if (
          !Set.of("DRAFT", "RECORDING", "READY").contains(
            Objects.toString(value, "")
          )
        ) bad("Неизвестный статус");
        w.status = value;
      }
      case "addTag" -> {
        if (
          value == null ||
          value.isBlank() ||
          value.length() > 60 ||
          w.tags.size() >= 20
        ) bad("Тег: от 1 до 60 символов, до 20 тегов");
        w.tags.add(value.trim());
      }
      case "removeTag" -> w.tags.remove(value);
      case "permanent" -> {
        if (!actor.roles().contains("ADMIN")) throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Удалять может только администратор"
        );
        if (!w.archived) bad("Сначала переместите произведение в архив");
        works.delete(w);
        works.flush();
        db.update(
          "INSERT INTO library_deleted(id) VALUES(?) ON CONFLICT DO NOTHING",
          id
        );
        db.update("DELETE FROM catalog_text_work WHERE id=?", id);
        audit(actor, "WORK_DELETED", id, w.title, correlation);
        return;
      }
      default -> bad("Неизвестная операция");
    }
    w.updatedAt = Instant.now();
    works.saveAndFlush(w);
    audit(
      actor,
      "WORK_" + operation.toUpperCase(Locale.ROOT),
      id,
      w.title,
      correlation
    );
  }

  public boolean isDeleted(UUID id) {
    return (
      db.queryForObject(
        "SELECT count(*) FROM library_deleted WHERE id=?",
        Integer.class,
        id
      ) > 0
    );
  }

  public void audit(
    Actor actor,
    String type,
    UUID id,
    String description,
    String correlation
  ) {
    db.update(
      "INSERT INTO library_audit VALUES(?,?,?,?,?,?,?,?,?)",
      UUID.randomUUID(),
      actor.id(),
      actor.name(),
      type,
      type.startsWith("COLLECTION_") ? "COLLECTION" : "TEXT_WORK",
      id,
      java.sql.Timestamp.from(Instant.now()),
      description.length() > 500 ? description.substring(0, 500) : description,
      correlation
    );
  }

  private static void bad(String text) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
  }
}
