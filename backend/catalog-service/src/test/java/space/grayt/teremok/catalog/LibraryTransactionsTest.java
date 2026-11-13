package space.grayt.teremok.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import space.grayt.teremok.catalog.library.domain.LibraryWork;
import space.grayt.teremok.catalog.library.dto.LibraryRequests.*;
import space.grayt.teremok.catalog.library.repository.Works;
import space.grayt.teremok.catalog.library.security.LibrarySecurity.Actor;
import space.grayt.teremok.catalog.library.service.*;

@SpringJUnitConfig(LibraryTransactionsTest.Config.class)
class LibraryTransactionsTest {

  @Autowired
  LibraryManagementService service;

  @Autowired
  LibraryService library;

  @Autowired
  Works works;

  LibraryService target;

  @Autowired
  JdbcTemplate db;

  final UUID work = UUID.randomUUID();
  final Actor admin = actor("ADMIN"),
    editor = actor("EDITOR"),
    stranger = actor("EDITOR"),
    reader = actor("READER");

  @BeforeEach
  void schema() {
    target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(
      library
    );
    reset(target, works);
    db.execute("DROP ALL OBJECTS");
    db.execute(
      "CREATE TABLE library_collection(id UUID PRIMARY KEY,name VARCHAR(120),active BOOLEAN)"
    );
    db.execute(
      "CREATE TABLE library_note(id UUID PRIMARY KEY,work_id UUID,author_id UUID,author VARCHAR(160),content VARCHAR(3000),pinned BOOLEAN,created_at TIMESTAMP,updated_at TIMESTAMP)"
    );
    db.execute(
      "CREATE TABLE library_audit(id UUID,actor_id UUID,actor VARCHAR(160),event_type VARCHAR(50),entity_type VARCHAR(50),entity_id UUID,occurred_at TIMESTAMP,description VARCHAR(500) CHECK(description<>'Отказ'),correlation_id VARCHAR(64))"
    );
    db.execute("CREATE TABLE bulk_probe(id UUID PRIMARY KEY)");
    var entity = new LibraryWork();
    entity.id = work;
    when(works.findById(work)).thenReturn(Optional.of(entity));
  }

  @Test
  void failedAuditRollsBackCollectionInsertAndUpdate() {
    assertThatThrownBy(() ->
      service.createCollection(
        new CollectionInput("Отказ", true),
        admin,
        "test"
      )
    ).isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("library_collection")).isZero();
    UUID id = id(
      service.createCollection(new CollectionInput("Было", true), admin, "test")
    );
    assertThatThrownBy(() ->
      service.updateCollection(
        id,
        new CollectionInput("Отказ", false),
        admin,
        "test"
      )
    ).isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
      db.queryForObject(
        "SELECT name FROM library_collection WHERE id=?",
        String.class,
        id
      )
    ).isEqualTo("Было");
    service.updateCollection(
      id,
      new CollectionInput("Стало", true),
      admin,
      "test"
    );
    assertThat(
      db.queryForObject(
        "SELECT count(*) FROM library_audit WHERE event_type='COLLECTION_UPDATED'",
        Integer.class
      )
    ).isOne();
  }

  @Test
  void noteOwnershipAndReaderRestrictionsAreEnforcedWithoutHttp() {
    UUID note = id(
      service.addNote(work, new NoteInput("Текст", false), editor, "test")
    );
    assertThatThrownBy(() ->
      service.editNote(
        work,
        note,
        new NoteInput("Чужой", false),
        stranger,
        "test"
      )
    )
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(e ->
        assertThat(
          ((ResponseStatusException) e).getStatusCode().value()
        ).isEqualTo(403)
      );
    assertThatThrownBy(() ->
      service.addNote(work, new NoteInput("Текст", false), reader, "test")
    ).isInstanceOf(ResponseStatusException.class);
    service.editNote(work, note, new NoteInput("Правка", true), editor, "test");
    service.deleteNote(work, note, admin, "test");
    assertThat(count("library_note")).isZero();
    assertThat(count("library_audit")).isEqualTo(3);
  }

  @Test
  void failedAuditRollsBackNoteEditAndDelete() {
    UUID note = id(
      service.addNote(work, new NoteInput("Было", false), editor, "test")
    );
    doThrow(new IllegalStateException("audit unavailable"))
      .when(target)
      .audit(any(), anyString(), any(), anyString(), anyString());
    assertThatThrownBy(() ->
      service.editNote(work, note, new NoteInput("Стало", true), editor, "test")
    ).isInstanceOf(IllegalStateException.class);
    assertThat(
      db.queryForObject(
        "SELECT content FROM library_note WHERE id=?",
        String.class,
        note
      )
    ).isEqualTo("Было");
    assertThatThrownBy(() ->
      service.deleteNote(work, note, editor, "test")
    ).isInstanceOf(IllegalStateException.class);
    assertThat(count("library_note")).isOne();
    assertThatThrownBy(() ->
      service.addNote(work, new NoteInput("Новое", false), editor, "test")
    ).isInstanceOf(IllegalStateException.class);
    assertThat(count("library_note")).isOne();
  }

  @Test
  void bulkCommitsSuccessfulRowsAndRollsBackOnlyFailedRows() {
    UUID first = UUID.randomUUID(),
      failed = UUID.randomUUID(),
      last = UUID.randomUUID();
    doAnswer(call -> {
      UUID id = call.getArgument(0);
      db.update("INSERT INTO bulk_probe VALUES(?)", id);
      if (id.equals(failed)) throw new IllegalStateException(
        "failure after write"
      );
      return null;
    })
      .when(target)
      .operation(
        any(),
        anyString(),
        nullable(String.class),
        nullable(Long.class),
        any(),
        anyString()
      );
    var result = (Map<?, ?>) service.bulk(
      new Bulk(List.of(first, failed, last), "archive", null),
      editor,
      "test"
    );
    assertThat(result.get("success")).isEqualTo(List.of(first, last));
    assertThat((List<?>) result.get("errors")).hasSize(1);
    assertThat(
      db.queryForList("SELECT id FROM bulk_probe", UUID.class)
    ).containsExactlyInAnyOrder(first, last);
  }

  int count(String table) {
    return db.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  UUID id(Object result) {
    return (UUID) ((Map<?, ?>) result).get("id");
  }

  static Actor actor(String role) {
    return new Actor(
      UUID.randomUUID(),
      "test",
      "Тест",
      Set.of(role),
      true,
      false
    );
  }

  @Configuration
  @EnableTransactionManagement
  static class Config {

    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
        "jdbc:h2:mem:transactions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "sa",
        ""
      );
    }

    @Bean
    JdbcTemplate db(DataSource source) {
      return new JdbcTemplate(source);
    }

    @Bean
    PlatformTransactionManager manager(DataSource source) {
      return new DataSourceTransactionManager(source);
    }

    @Bean
    Works works() {
      return mock(Works.class);
    }

    @Bean
    LibraryService library(Works works, JdbcTemplate db) {
      return spy(new LibraryService(works, db));
    }

    @Bean
    LibraryManagementService service(
      Works works,
      LibraryService library,
      JdbcTemplate db,
      PlatformTransactionManager manager
    ) {
      return new LibraryManagementService(works, library, db, manager);
    }
  }
}
