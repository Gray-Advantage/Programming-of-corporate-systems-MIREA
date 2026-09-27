package space.grayt.teremok;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.audio.FakeAudioPlayer;
import space.grayt.teremok.audio.FakeAudioRecorder;
import space.grayt.teremok.cli.Console;

/** A disk failure must not escape as a raw exception. */
class AppTest {

    @Test
    void unavailableDataDirectoryIsExplainedInWords(@TempDir Path dir) throws Exception {
        Path data = dir.resolve("data");
        Files.write(data, List.of("это файл, а не каталог"), UTF_8);

        String printed = run(data, "0\n");

        assertTrue(printed.contains("Нет доступа к каталогу данных"), () -> printed);
    }

    /**
     * profiles.txt is replaced by a directory: reading skips it, but writing a new profile
     * raises StorageException from deep inside storage.
     */
    @Test
    void diskWriteFailureEndsWithMessage(@TempDir Path dir) throws Exception {
        Path data = dir.resolve("data");
        Files.createDirectories(data.resolve("profiles.txt"));
        Files.write(data.resolve("profiles.txt").resolve("занято.txt"), List.of("х"), UTF_8);

        String printed = run(data, "n\nmasha\n");

        assertTrue(printed.contains("Не удалось записать файл"), () -> printed);
        assertTrue(printed.contains("Работа завершена."), () -> printed);
    }

    private static String run(Path data, String input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        assertDoesNotThrow(() -> new App(data, console, new FakeAudioRecorder(), new FakeAudioPlayer(),
                Clock.systemUTC()).run());
        return out.toString(UTF_8);
    }
}
