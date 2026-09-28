package space.grayt.teremok;

import java.nio.file.Path;
import java.time.Clock;
import space.grayt.teremok.audio.JavaSoundPlayer;
import space.grayt.teremok.audio.JavaSoundRecorder;
import space.grayt.teremok.backend.BackendClient;
import space.grayt.teremok.cli.Console;
import space.grayt.teremok.config.Environment;
import space.grayt.teremok.db.DatabaseConfig;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.storage.AudioStorage;
import space.grayt.teremok.textwork.BackendTextWorkCatalog;
import space.grayt.teremok.textwork.JdbcTextWorkCatalog;
import space.grayt.teremok.textwork.TextWorkCatalog;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Environment environment = Environment.load();
        DatabaseManager database = new DatabaseManager(DatabaseConfig.from(environment));
        new App(
                database,
                textWorks(environment, database),
                new AudioStorage(Path.of(environment.get("AUDIO_DIR", "data/audio"))),
                Console.system(),
                new JavaSoundRecorder(),
                new JavaSoundPlayer(),
                Clock.systemUTC()).run();
    }

    /** Text works come from the database; TEXT_WORKS_SOURCE=backend takes them from the backend services. */
    private static TextWorkCatalog textWorks(Environment environment, DatabaseManager database) {
        if (environment.get("TEXT_WORKS_SOURCE", "database").equalsIgnoreCase("backend")) {
            return new BackendTextWorkCatalog(BackendClient.fromEnvironment());
        }
        return new JdbcTextWorkCatalog(database);
    }
}
