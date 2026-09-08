package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.FileProfileRepository;
import space.grayt.teremok.storage.ProfileRepository;

class ProfileScreenTest {

    private ByteArrayOutputStream out;

    private ProfileScreen screen(String input, ProfileRepository profiles) {
        out = new ByteArrayOutputStream();
        return new ProfileScreen(new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out), profiles);
    }

    private String printed() {
        return out.toString(UTF_8);
    }

    @Test
    void создаётПрофильПоКомандеN(@TempDir Path dir) {
        ProfileRepository profiles = new FileProfileRepository(dir);

        Optional<Profile> chosen = screen("n\nСергей\n", profiles).choose();

        assertEquals("сергей", chosen.orElseThrow().id());
        assertEquals(1, profiles.findAll().size());
    }

    @Test
    void выбираетСуществующийПрофильПоНомеру(@TempDir Path dir) {
        ProfileRepository profiles = new FileProfileRepository(dir);
        profiles.create("Сергей");
        profiles.create("Маша");

        assertEquals("маша", screen("2\n", profiles).choose().orElseThrow().id());
    }

    @Test
    void нольЗакрываетПриложение(@TempDir Path dir) {
        assertTrue(screen("0\n", new FileProfileRepository(dir)).choose().isEmpty());
    }

    @Test
    void плохоеИмяОбъясняетсяИСпрашиваетсяСнова(@TempDir Path dir) {
        ProfileRepository profiles = new FileProfileRepository(dir);

        Optional<Profile> chosen = screen("n\nСерёжа Петров\nn\nСерёжа\n", profiles).choose();

        assertEquals("серёжа", chosen.orElseThrow().id());
        assertTrue(printed().contains("от 1 до 24 символов"));
    }

    @Test
    void непонятныйВводНеЛомаетЭкран(@TempDir Path dir) {
        ProfileRepository profiles = new FileProfileRepository(dir);
        profiles.create("Сергей");

        assertEquals("сергей", screen("абв\n99\n1\n", profiles).choose().orElseThrow().id());
    }
}
