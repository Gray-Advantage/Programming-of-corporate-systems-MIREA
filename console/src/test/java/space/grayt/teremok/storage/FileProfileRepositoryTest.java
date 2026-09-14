package space.grayt.teremok.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.domain.Profile;

class FileProfileRepositoryTest {

    @Test
    void createsAndReturnsProfile(@TempDir Path dir) {
        ProfileRepository repository = new FileProfileRepository(dir);

        Profile created = repository.create("Сергей");

        assertEquals("сергей", created.id());
        assertEquals("Сергей", created.name());
        assertEquals(List.of(created), repository.findAll());
    }

    @Test
    void profileSurvivesRestart(@TempDir Path dir) {
        new FileProfileRepository(dir).create("Маша");

        ProfileRepository reopened = new FileProfileRepository(dir);

        assertEquals("Маша", reopened.findById("маша").orElseThrow().name());
    }

    @Test
    void caseInsensitiveDuplicateIsRejected(@TempDir Path dir) {
        ProfileRepository repository = new FileProfileRepository(dir);
        repository.create("Сергей");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> repository.create("СЕРГЕЙ"));

        assertTrue(error.getMessage().contains("уже"));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void emptyDirectoryHasNoProfiles(@TempDir Path dir) {
        assertEquals(List.of(), new FileProfileRepository(dir).findAll());
    }

    @Test
    void unknownProfileIsNotFound(@TempDir Path dir) {
        assertTrue(new FileProfileRepository(dir).findById("никто").isEmpty());
    }

    @Test
    void namesWithSpacesAndSpecialCharactersAreRejected(@TempDir Path dir) {
        ProfileRepository repository = new FileProfileRepository(dir);

        assertThrows(IllegalArgumentException.class, () -> repository.create("Серёжа Петров"));
        assertThrows(IllegalArgumentException.class, () -> repository.create("вася=петя"));
        assertThrows(IllegalArgumentException.class, () -> repository.create(""));
        assertThrows(IllegalArgumentException.class, () -> repository.create("а".repeat(25)));
    }

    @Test
    void lettersDigitsHyphenAndUnderscoreAreAllowed(@TempDir Path dir) {
        ProfileRepository repository = new FileProfileRepository(dir);

        assertEquals("маша-2_вторая", repository.create("Маша-2_вторая").id());
    }

    @Test
    void profileNameIsFoundById(@TempDir Path dir) {
        ProfileRepository repository = new FileProfileRepository(dir);
        repository.create("Сергей");

        assertEquals("Сергей", repository.nameOf("сергей"));
    }

    @Test
    void unknownProfileNameFallsBackToId(@TempDir Path dir) {
        assertEquals("никто", new FileProfileRepository(dir).nameOf("никто"));
    }
}
