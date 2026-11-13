package space.grayt.teremok.catalog.library.service;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import space.grayt.teremok.catalog.library.dto.*;
import space.grayt.teremok.catalog.library.dto.LibraryRequests.*;
import space.grayt.teremok.catalog.library.filter.WorkFilter;
import space.grayt.teremok.catalog.library.repository.Works;
import space.grayt.teremok.catalog.library.security.LibrarySecurity.Actor;

/** Application use cases shared by HTTP adapters; mutations and audit commit together. */
@Service
public class LibraryManagementService {

  private final Works works;
  private final LibraryService library;
  private final JdbcTemplate db;
  private final TransactionTemplate transactions;

  public LibraryManagementService(
    Works works,
    LibraryService library,
    JdbcTemplate db,
    org.springframework.transaction.PlatformTransactionManager manager
  ) {
    this.works = works;
    this.library = library;
    this.db = db;
    this.transactions = new TransactionTemplate(manager);
    this.transactions.setPropagationBehavior(
      org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
    );
  }

  @Transactional(readOnly = true)
  public Object list(
    WorkFilter filter,
    int page,
    int size,
    String sort,
    String direction
  ) {
    String field = Set.of(
      "title",
      "author",
      "createdAt",
      "updatedAt",
      "targetMinutes",
      "code"
    ).contains(sort)
      ? sort
      : "title";
    return works
      .findAll(
        filter.specification(),
        PageRequest.of(
          Math.max(page, 0),
          Math.clamp(size, 1, 100),
          Sort.by(
            "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
            field
          ).and(Sort.by("id"))
        )
      )
      .map(WorkView::from);
  }

  @Transactional(readOnly = true)
  public WorkView detail(UUID id) {
    return WorkView.from(library.required(id));
  }

  public Object bulk(Bulk input, Actor actor, String correlation) {
    requireEditor(actor);
    var success = new ArrayList<UUID>();
    var errors = new ArrayList<Map<String, Object>>();
    for (var id : input.ids()) {
      try {
        transactions.executeWithoutResult(s ->
          library.operation(
            id,
            input.operation(),
            input.value(),
            null,
            actor,
            correlation
          )
        );
        success.add(id);
      } catch (Exception e) {
        errors.add(
          Map.of(
            "id",
            id,
            "message",
            e instanceof ResponseStatusException r
              ? Objects.toString(r.getReason(), "Операция отклонена")
              : "Запись изменилась или содержит конфликтующие данные"
          )
        );
      }
    }
    return Map.of("success", success, "errors", errors);
  }

  @Transactional(readOnly = true)
  public Object collections() {
    return db.queryForList("SELECT * FROM library_collection ORDER BY name");
  }

  @Transactional
  public Object createCollection(
    CollectionInput input,
    Actor actor,
    String correlation
  ) {
    requireAdmin(actor);
    UUID id = UUID.randomUUID();
    db.update(
      "INSERT INTO library_collection VALUES(?,?,?)",
      id,
      input.name().trim(),
      input.active()
    );
    library.audit(actor, "COLLECTION_CREATED", id, input.name(), correlation);
    return Map.of("id", id);
  }

  @Transactional
  public void updateCollection(
    UUID id,
    CollectionInput input,
    Actor actor,
    String correlation
  ) {
    requireAdmin(actor);
    if (
      db.update(
        "UPDATE library_collection SET name=?,active=? WHERE id=?",
        input.name().trim(),
        input.active(),
        id
      ) == 0
    ) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Коллекция не найдена"
    );

    library.audit(actor, "COLLECTION_UPDATED", id, input.name(), correlation);
  }

  @Transactional(readOnly = true)
  public Object notes(UUID id) {
    library.required(id);
    return db.queryForList(
      "SELECT * FROM library_note WHERE work_id=? ORDER BY pinned DESC,created_at DESC",
      id
    );
  }

  @Transactional
  public Object addNote(
    UUID id,
    NoteInput input,
    Actor actor,
    String correlation
  ) {
    requireEditor(actor);
    library.required(id);
    UUID note = UUID.randomUUID();
    var a = actor;
    db.update(
      "INSERT INTO library_note VALUES(?,?,?,?,?,?,now(),now())",
      note,
      id,
      a.id(),
      a.name(),
      input.content(),
      input.pinned()
    );
    library.audit(a, "NOTE_CREATED", id, "Добавлена заметка", correlation);
    return Map.of("id", note);
  }

  @Transactional
  public void editNote(
    UUID id,
    UUID noteId,
    NoteInput input,
    Actor actor,
    String correlation
  ) {
    requireEditor(actor);
    checkNote(id, noteId, actor);
    db.update(
      "UPDATE library_note SET content=?,pinned=?,updated_at=now() WHERE id=?",
      input.content(),
      input.pinned(),
      noteId
    );
    library.audit(actor, "NOTE_UPDATED", id, "Изменена заметка", correlation);
  }

  @Transactional
  public void deleteNote(
    UUID id,
    UUID noteId,
    Actor actor,
    String correlation
  ) {
    requireEditor(actor);
    checkNote(id, noteId, actor);
    db.update("DELETE FROM library_note WHERE id=?", noteId);
    library.audit(actor, "NOTE_DELETED", id, "Удалена заметка", correlation);
  }

  private void checkNote(UUID id, UUID note, Actor actor) {
    var rows = db.queryForList(
      "SELECT author_id FROM library_note WHERE id=? AND work_id=? FOR UPDATE",
      note,
      id
    );
    if (rows.isEmpty()) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Заметка не найдена"
    );
    if (
      !rows.getFirst().get("author_id").equals(actor.id()) &&
      !actor.roles().contains("ADMIN")
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Изменять можно только свои заметки"
    );
  }

  @Transactional(readOnly = true)
  public Object analytics() {
    return Map.of(
      "totals",
      db.queryForMap(
        "SELECT count(*) AS total,count(*) FILTER(WHERE NOT archived) AS active,count(*) FILTER(WHERE archived) AS archived,count(*) FILTER(WHERE featured) AS featured,coalesce(avg(target_minutes),0) AS average_minutes FROM library_work"
      ),
      "byStatus",
      db.queryForList(
        "SELECT status AS name,count(*) AS count FROM library_work GROUP BY status ORDER BY status"
      ),
      "byComplexity",
      db.queryForList(
        "SELECT complexity AS name,count(*) AS count FROM library_work GROUP BY complexity ORDER BY complexity"
      ),
      "byCollection",
      db.queryForList(
        "SELECT c.name,count(w.id) AS count FROM library_collection c LEFT JOIN library_work w ON c.id=w.collection_id GROUP BY c.id,c.name ORDER BY c.name"
      )
    );
  }

  @Transactional(readOnly = true)
  public Object audit(
    Actor actor,
    String q,
    int page,
    int size,
    String direction
  ) {
    requireAdmin(actor);
    String search = "%" + q.toLowerCase(Locale.ROOT) + "%";
    int limit = Math.clamp(size, 1, 100);
    return Map.of(
      "content",
      db.queryForList(
        "SELECT * FROM library_audit WHERE lower(actor||' '||event_type||' '||description) LIKE ? ORDER BY occurred_at " +
          ("asc".equals(direction) ? "ASC" : "DESC") +
          " LIMIT ? OFFSET ?",
        search,
        limit,
        (long) Math.max(0, page) * limit
      ),
      "totalElements",
      db.queryForObject(
        "SELECT count(*) FROM library_audit WHERE lower(actor||' '||event_type||' '||description) LIKE ?",
        Long.class,
        search
      )
    );
  }

  private static void requireAdmin(Actor actor) {
    if (
      !actor.active() ||
      actor.mustChangePassword() ||
      !actor.roles().contains("ADMIN")
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Требуются права администратора"
    );
  }

  private static void requireEditor(Actor actor) {
    if (
      !actor.active() ||
      actor.mustChangePassword() ||
      (!actor.roles().contains("EDITOR") && !actor.roles().contains("ADMIN"))
    ) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Нужны права редактора"
    );
  }
}
