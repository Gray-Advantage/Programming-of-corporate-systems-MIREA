package space.grayt.teremok.catalog;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.catalog.library.domain.LibraryWork;
import space.grayt.teremok.catalog.library.filter.WorkFilter;
import space.grayt.teremok.catalog.library.repository.Works;
import space.grayt.teremok.catalog.library.service.LibraryService;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.bootstrap-servers=localhost:19092",
  }
)
class LibraryIntegrationTest {

  @Autowired
  Works works;

  @Test
  void complexityUsesBothDurationAndCastSize() {
    assertThat(LibraryService.complexity(3, 1)).isEqualTo("LOW");
    assertThat(LibraryService.complexity(3, 3)).isEqualTo("MEDIUM");
    assertThat(LibraryService.complexity(30, 1)).isEqualTo("HIGH");
    assertThat(LibraryService.complexity(3, 6)).isEqualTo("HIGH");
  }

  @Test
  @Transactional
  void filtersSearchLiteralWildcardsAndExcludeArchive() {
    for (String title : new String[] {
      "100% история",
      "1000 история",
      "В архиве",
    }) {
      var w = new LibraryWork();
      w.id = UUID.randomUUID();
      w.code = w.id.toString();
      w.title = title;
      w.author = "Автор";
      w.collectionId = UUID.randomUUID();
      w.targetMinutes = 5;
      w.rolesCount = 1;
      w.complexity = "LOW";
      w.archived = title.equals("В архиве");
      w.createdAt = Instant.now();
      w.updatedAt = w.createdAt;
      works.saveAndFlush(w);
    }
    var literal = new WorkFilter(
      "100%",
      null,
      null,
      null,
      null,
      null,
      "active",
      null,
      null,
      null,
      null
    );
    assertThat(works.findAll(literal.specification()))
      .extracting(w -> w.title)
      .containsExactly("100% история");
    assertThat(
      works.findAll(
        new WorkFilter(
          null,
          null,
          null,
          null,
          null,
          null,
          "active",
          null,
          null,
          null,
          null
        ).specification()
      )
    ).hasSize(2);
  }
}
