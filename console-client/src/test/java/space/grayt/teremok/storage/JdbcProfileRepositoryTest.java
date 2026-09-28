package space.grayt.teremok.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.db.TestDatabase;
import space.grayt.teremok.domain.Profile;

class JdbcProfileRepositoryTest {

    private DatabaseManager database;
    private ProfileRepository repository;

    @BeforeEach
    void setUp() {
        database = TestDatabase.empty();
        repository = new JdbcProfileRepository(database);
    }

    @Test
    void createsAndReturnsProfile() {
        Profile created = repository.create("Сергей");

        assertEquals("сергей", created.id());
        assertEquals("Сергей", created.name());
        assertEquals(List.of(created), repository.findAll());
    }

    @Test
    void profileIsKeptInDatabase() {
        repository.create("Маша");

        ProfileRepository reopened = new JdbcProfileRepository(database);

        assertEquals("Маша", reopened.findById("маша").orElseThrow().name());
    }

    @Test
    void profilesAreListedInCreationOrder() {
        repository.create("Сергей");
        repository.create("Аня");

        assertEquals(List.of("Сергей", "Аня"), repository.findAll().stream().map(Profile::name).toList());
    }

    @Test
    void caseInsensitiveDuplicateIsRejected() {
        repository.create("Сергей");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> repository.create("СЕРГЕЙ"));

        assertTrue(error.getMessage().contains("уже"));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void emptyDatabaseHasNoProfiles() {
        assertEquals(List.of(), repository.findAll());
    }

    @Test
    void unknownProfileIsNotFound() {
        assertTrue(repository.findById("никто").isEmpty());
    }

    @Test
    void namesWithSpacesAndSpecialCharactersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> repository.create("Серёжа Петров"));
        assertThrows(IllegalArgumentException.class, () -> repository.create("вася=петя"));
        assertThrows(IllegalArgumentException.class, () -> repository.create(""));
        assertThrows(IllegalArgumentException.class, () -> repository.create("а".repeat(25)));
        assertEquals(List.of(), repository.findAll());
    }

    @Test
    void lettersDigitsHyphenAndUnderscoreAreAllowed() {
        assertEquals("маша-2_вторая", repository.create("Маша-2_вторая").id());
    }

    @Test
    void profileNameIsFoundById() {
        repository.create("Сергей");

        assertEquals("Сергей", repository.nameOf("сергей"));
    }

    @Test
    void unknownProfileNameFallsBackToId() {
        assertEquals("никто", repository.nameOf("никто"));
    }

    /** The table check id = LOWER(name) guards profiles written past the repository. */
    @Test
    void databaseRejectsIdThatIsNotLowerCaseName() {
        assertThrows(IllegalStateException.class, () -> TestDatabase.execute(database,
                "INSERT INTO profiles (id, name) VALUES ('sergey', 'Сергей')"));
    }

    @Test
    void sqlFailureBecomesStorageExceptionWithRussianMessage() {
        TestDatabase.execute(database, "DROP TABLE profiles CASCADE");

        StorageException error = assertThrows(StorageException.class, repository::findAll);

        assertTrue(error.getMessage().startsWith("Не удалось прочитать профили"), error::getMessage);
    }
}
