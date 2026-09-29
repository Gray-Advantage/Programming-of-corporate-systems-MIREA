package space.grayt.teremok.textwork;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.db.TestDatabase;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.VoicePart;
import space.grayt.teremok.storage.StorageException;

class JdbcTextWorkCatalogTest {

    private static final String KOLOBOK = "30000000-0000-0000-0000-000000000001";

    private final TextWorkCatalog seeded = new JdbcTextWorkCatalog(TestDatabase.withTextWorks());

    @Test
    void loadsSeedTextWorksOrderedByTitle() {
        assertEquals(List.of("Колобок", "Красная Шапочка", "Теремок"),
                seeded.all().stream().map(TextWork::title).toList());
        assertTrue(seeded.warnings().isEmpty(), () -> String.join("; ", seeded.warnings()));
    }

    @Test
    void fragmentsAreNumberedFromOneInOrder() {
        TextWork kolobok = seeded.find(KOLOBOK).orElseThrow();

        assertEquals(18, kolobok.fragments().size());
        for (int i = 0; i < kolobok.fragments().size(); i++) {
            assertEquals(i + 1, kolobok.fragments().get(i).number());
        }
        assertEquals("Жили старик со старухой, и не было у них ни крошки хлеба.",
                kolobok.fragments().get(0).text());
    }

    @Test
    void voicePartsFollowTheirFirstFragment() {
        TextWork kolobok = seeded.find(KOLOBOK).orElseThrow();

        assertEquals(List.of("Рассказчик", "Старик", "Старуха", "Колобок", "Заяц", "Волк", "Медведь", "Лиса"),
                kolobok.voiceParts().stream().map(VoicePart::name).toList());
        assertEquals(List.of(13, 15), kolobok.fragmentsOf(TestDatabase.voicePartId(kolobok, "Лиса")).stream()
                .map(TextWorkFragment::number).toList());
    }

    @Test
    void unknownTextWorkIsNotFound() {
        assertTrue(seeded.find("00000000-0000-0000-0000-000000000000").isEmpty());
    }

    @Test
    void emptyDatabaseHasNoTextWorks() {
        assertEquals(List.of(), new JdbcTextWorkCatalog(TestDatabase.empty()).all());
    }

    @Test
    void textWorkWithoutFragmentsIsSkippedWithWarning() {
        DatabaseManager database = TestDatabase.empty();
        TestDatabase.execute(database, "INSERT INTO text_works (id, title, authors, language) VALUES "
                + "('40000000-0000-0000-0000-000000000001', 'Пустая', 'Никто', 'rus')");
        TestDatabase.addTextWork(database, "Репка", "Дед: Тянем-потянем.");

        TextWorkCatalog catalog = new JdbcTextWorkCatalog(database);

        assertEquals(List.of("Репка"), catalog.all().stream().map(TextWork::title).toList());
        assertTrue(catalog.warnings().get(0).contains("Пустая"), () -> catalog.warnings().toString());
    }

    @Test
    void fragmentWithVoicePartOfAnotherTextWorkSkipsTextWork() {
        DatabaseManager database = TestDatabase.empty();
        TextWork turnip = TestDatabase.addTextWork(database, "Репка", "Дед: Тянем-потянем.");
        TextWork hen = TestDatabase.addTextWork(database, "Курочка Ряба", "Дед: Бил-бил, не разбил.");
        TestDatabase.execute(database, "UPDATE text_work_fragments SET voice_part_id = '"
                + turnip.fragments().get(0).voicePartId() + "' WHERE text_work_id = '" + hen.id() + "'");

        TextWorkCatalog catalog = new JdbcTextWorkCatalog(database);

        assertEquals(List.of("Репка"), catalog.all().stream().map(TextWork::title).toList());
        assertTrue(catalog.warnings().get(0).contains("Курочка Ряба"), () -> catalog.warnings().toString());
    }

    /** Loading is lazy: the catalog is created before App checks the database. */
    @Test
    void failureSurfacesOnFirstUseAsStorageException() {
        DatabaseManager database = TestDatabase.withTextWorks();
        TextWorkCatalog catalog = new JdbcTextWorkCatalog(database);
        TestDatabase.execute(database, "DROP TABLE text_work_fragments CASCADE");

        StorageException error = assertThrows(StorageException.class, catalog::all);

        assertTrue(error.getMessage().startsWith("Не удалось загрузить произведения"), error::getMessage);
    }

    @Test
    void wholeSeedLoadsIntoH2() {
        assertEquals(3, new JdbcTextWorkCatalog(TestDatabase.withSeed()).all().size());
    }
}
