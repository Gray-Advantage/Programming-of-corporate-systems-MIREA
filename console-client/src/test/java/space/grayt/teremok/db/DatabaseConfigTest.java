package space.grayt.teremok.db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.config.Environment;

class DatabaseConfigTest {

    @Test
    void defaultsPointToLocalTeremokDatabase(@TempDir Path dir) {
        DatabaseConfig config = DatabaseConfig.from(Environment.of(Map.of(), dir));

        assertEquals("jdbc:postgresql://localhost:5432/teremok", config.url());
        assertEquals("teremok", config.user());
        assertEquals("teremok", config.password());
    }

    @Test
    void urlIsAssembledFromVariables(@TempDir Path dir) {
        DatabaseConfig config = DatabaseConfig.from(Environment.of(Map.of(
                "POSTGRES_HOST", "db.local",
                "POSTGRES_PORT", "6000",
                "POSTGRES_DB", "tales",
                "POSTGRES_USER", "reader",
                "POSTGRES_PASSWORD", "secret"), dir));

        assertEquals("jdbc:postgresql://db.local:6000/tales", config.url());
        assertEquals("reader", config.user());
        assertEquals("secret", config.password());
    }

    /** The repository .env of this Mac moves PostgreSQL to 5433; the client must follow it. */
    @Test
    void portComesFromDotEnvOfRepositoryRoot(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(".env"), List.of(
                "# Copy to .env and adjust.",
                "POSTGRES_DB=teremok",
                "POSTGRES_USER=teremok",
                "POSTGRES_PASSWORD=teremok",
                "POSTGRES_PORT=5433"), UTF_8);
        Path consoleClient = Files.createDirectories(dir.resolve("console-client"));

        DatabaseConfig config = DatabaseConfig.from(Environment.of(Map.of(), consoleClient));

        assertEquals("jdbc:postgresql://localhost:5433/teremok", config.url());
    }

    @Test
    void dbUrlOverridesWholeUrl(@TempDir Path dir) {
        DatabaseConfig config = DatabaseConfig.from(Environment.of(Map.of(
                "DB_URL", "jdbc:postgresql://remote:5432/other",
                "POSTGRES_PORT", "6000"), dir));

        assertEquals("jdbc:postgresql://remote:5432/other", config.url());
    }

    @Test
    void passwordIsHiddenInMessages() {
        DatabaseConfig config = new DatabaseConfig(
                "jdbc:postgresql://localhost/teremok?user=teremok&password=secret&ssl=false", "teremok", "secret");

        assertEquals("jdbc:postgresql://localhost/teremok?user=teremok&password=***&ssl=false", config.displayUrl());
        assertFalse(config.toString().contains("secret"), config::toString);
    }
}
