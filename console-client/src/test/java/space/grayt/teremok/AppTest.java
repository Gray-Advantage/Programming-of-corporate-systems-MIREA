package space.grayt.teremok;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.audio.FakeAudioPlayer;
import space.grayt.teremok.audio.FakeAudioRecorder;
import space.grayt.teremok.cli.Console;
import space.grayt.teremok.db.DatabaseConfig;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.db.TestDatabase;
import space.grayt.teremok.storage.AudioStorage;
import space.grayt.teremok.textwork.JdbcTextWorkCatalog;

/** Database failures must end in words, not in a raw exception. */
class AppTest {

    @Test
    void unreachableDatabaseIsExplainedWithHint(@TempDir Path dir) {
        DatabaseManager database = new DatabaseManager(
                new DatabaseConfig("jdbc:postgresql://localhost:1/teremok", "teremok", "teremok"));

        String printed = run(database, dir, "0\n");

        assertTrue(printed.contains("Не удалось подключиться к базе данных jdbc:postgresql://localhost:1/teremok"),
                () -> printed);
        assertTrue(printed.contains("docker compose up -d postgres"), () -> printed);
        assertFalse(printed.contains("Кто вы?"), () -> printed);
    }

    /** The profiles table is gone: the failure surfaces from deep inside a repository. */
    @Test
    void storageFailureEndsWithMessage(@TempDir Path dir) {
        DatabaseManager database = TestDatabase.withTextWorks();
        TestDatabase.execute(database, "DROP TABLE profiles CASCADE");

        String printed = run(database, dir, "0\n");

        assertTrue(printed.contains("Не удалось прочитать профили"), () -> printed);
        assertTrue(printed.contains("Работа завершена."), () -> printed);
    }

    @Test
    void quittingRightAway(@TempDir Path dir) {
        String printed = run(TestDatabase.withTextWorks(), dir, "0\n");

        assertTrue(printed.contains("Кто вы?"), () -> printed);
        assertTrue(printed.contains("До встречи"), () -> printed);
    }

    private static String run(DatabaseManager database, Path dir, String input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        assertDoesNotThrow(() -> new App(database, new JdbcTextWorkCatalog(database), new AudioStorage(dir), console,
                new FakeAudioRecorder(), new FakeAudioPlayer(), Clock.systemUTC()).run());
        return out.toString(UTF_8);
    }
}
