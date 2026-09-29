package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.app.ProfileService;
import space.grayt.teremok.db.TestDatabase;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.JdbcProfileRepository;

class ProfileScreenTest {

    private ProfileService profiles;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp() {
        profiles = new ProfileService(new JdbcProfileRepository(TestDatabase.empty()));
    }

    private ProfileScreen screen(String input) {
        out = new ByteArrayOutputStream();
        return new ProfileScreen(new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out), profiles);
    }

    private String printed() {
        return out.toString(UTF_8);
    }

    @Test
    void createsProfileWithCommandN() {
        Optional<Profile> chosen = screen("n\nСергей\n").choose();

        assertEquals("сергей", chosen.orElseThrow().id());
        assertEquals(1, profiles.all().size());
    }

    @Test
    void selectsExistingProfileByNumber() {
        profiles.create("Сергей");
        profiles.create("Маша");

        assertEquals("маша", screen("2\n").choose().orElseThrow().id());
    }

    @Test
    void zeroQuitsApplication() {
        assertTrue(screen("0\n").choose().isEmpty());
    }

    @Test
    void invalidNameIsExplainedAndAskedAgain() {
        Optional<Profile> chosen = screen("n\nСерёжа Петров\nn\nСерёжа\n").choose();

        assertEquals("серёжа", chosen.orElseThrow().id());
        assertTrue(printed().contains("от 1 до 24 символов"));
    }

    @Test
    void duplicateNameIsExplained() {
        profiles.create("Сергей");

        Optional<Profile> chosen = screen("n\nсергей\n1\n").choose();

        assertEquals("Сергей", chosen.orElseThrow().name());
        assertTrue(printed().contains("Профиль с таким именем уже есть"), this::printed);
        assertEquals(1, profiles.all().size());
    }

    @Test
    void closedInputAtProfileNameCreatesNoProfile() {
        Optional<Profile> chosen = screen("n\n").choose();

        assertTrue(chosen.isEmpty());
        assertTrue(profiles.all().isEmpty());
    }

    @Test
    void unknownInputDoesNotBreakScreen() {
        profiles.create("Сергей");

        assertEquals("сергей", screen("абв\n99\n1\n").choose().orElseThrow().id());
    }
}
