package space.grayt.teremok.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentTest {

    @Test
    void variableWinsOverDotEnv(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(".env"), List.of("POSTGRES_PORT=5433"), UTF_8);

        Environment environment = Environment.of(Map.of("POSTGRES_PORT", "6000"), dir);

        assertEquals("6000", environment.get("POSTGRES_PORT", "5432"));
    }

    @Test
    void missingVariableIsTakenFromDotEnv(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(".env"), List.of("POSTGRES_PORT=5433"), UTF_8);

        assertEquals("5433", Environment.of(Map.of(), dir).get("POSTGRES_PORT", "5432"));
    }

    @Test
    void blankVariableCountsAsUnset(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(".env"), List.of("POSTGRES_PORT=5433"), UTF_8);

        assertEquals("5433", Environment.of(Map.of("POSTGRES_PORT", " "), dir).get("POSTGRES_PORT", "5432"));
    }

    @Test
    void fallbackWhenNeitherIsSet(@TempDir Path dir) {
        assertEquals("5432", Environment.of(Map.of(), dir).get("POSTGRES_PORT", "5432"));
    }

    /** ./gradlew run works in console-client/, while .env lies in the repository root. */
    @Test
    void dotEnvIsFoundInParentDirectory(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(".env"), List.of("POSTGRES_PORT=5433"), UTF_8);
        Path working = Files.createDirectories(dir.resolve("console-client"));

        assertEquals("5433", Environment.of(Map.of(), working).get("POSTGRES_PORT", "5432"));
    }

    @Test
    void nearestDotEnvWins(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(".env"), List.of("POSTGRES_PORT=5433"), UTF_8);
        Path working = Files.createDirectories(dir.resolve("console-client"));
        Files.write(working.resolve(".env"), List.of("POSTGRES_PORT=6543"), UTF_8);

        assertEquals("6543", Environment.of(Map.of(), working).get("POSTGRES_PORT", "5432"));
    }

    @Test
    void dotEnvIsSearchedAtMostThreeLevelsUp(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(".env"), List.of("POSTGRES_PORT=5433"), UTF_8);
        Path threeUp = Files.createDirectories(dir.resolve("a/b/c"));
        Path fourUp = Files.createDirectories(dir.resolve("a/b/c/d"));

        assertEquals("5433", Environment.of(Map.of(), threeUp).get("POSTGRES_PORT", "5432"));
        assertEquals("5432", Environment.of(Map.of(), fourUp).get("POSTGRES_PORT", "5432"));
    }

    @Test
    void parserSkipsCommentsBlankAndMalformedLines() {
        Map<String, String> values = Environment.parse(List.of(
                "# comment",
                "",
                "POSTGRES_DB=teremok",
                "not a pair",
                "=no key",
                "  POSTGRES_USER = admin  "));

        assertEquals(Map.of("POSTGRES_DB", "teremok", "POSTGRES_USER", "admin"), values);
    }

    @Test
    void parserDropsQuotesExportAndInlineComments() {
        Map<String, String> values = Environment.parse(List.of(
                "POSTGRES_PASSWORD=\"se cret\"",
                "POSTGRES_HOST='db.local'",
                "export POSTGRES_PORT=5433",
                "POSTGRES_DB=teremok # the database",
                "DB_URL=jdbc:postgresql://host/db?a=b"));

        assertEquals("se cret", values.get("POSTGRES_PASSWORD"));
        assertEquals("db.local", values.get("POSTGRES_HOST"));
        assertEquals("5433", values.get("POSTGRES_PORT"));
        assertEquals("teremok", values.get("POSTGRES_DB"));
        assertEquals("jdbc:postgresql://host/db?a=b", values.get("DB_URL"));
    }
}
