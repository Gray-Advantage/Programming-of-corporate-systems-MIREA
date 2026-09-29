package space.grayt.teremok.db;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.VoicePart;
import space.grayt.teremok.textwork.TextWorkCatalog;

/**
 * Fresh in-memory H2 databases in PostgreSQL mode, built from the same database/schema.sql and
 * seed.sql as the Docker PostgreSQL. Every call gets its own database, so tests stay independent.
 */
public final class TestDatabase {

    private static final String H2_OPTIONS =
            ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    private static final Pattern TEXT_WORK_INSERT =
            Pattern.compile("INSERT INTO (text_works|voice_parts|text_work_fragments)\\b");

    private TestDatabase() {
    }

    public static DatabaseConfig config() {
        return new DatabaseConfig("jdbc:h2:mem:teremok-" + UUID.randomUUID() + H2_OPTIONS, "teremok", "teremok");
    }

    /** Only the schema: no text works, profiles or voicings. */
    public static DatabaseManager empty() {
        DatabaseManager database = new DatabaseManager(config());
        execute(database, resource("/database/schema.sql"));
        return database;
    }

    /** The schema and the three fairy tales of seed.sql, without its profiles, voicings and votes. */
    public static DatabaseManager withTextWorks() {
        DatabaseManager database = empty();
        for (String statement : statements(resource("/database/seed.sql"))) {
            if (TEXT_WORK_INSERT.matcher(statement).lookingAt()) {
                execute(database, statement);
            }
        }
        return database;
    }

    /** Everything PostgreSQL gets in Docker: the schema and the whole seed.sql. */
    public static DatabaseManager withSeed() {
        DatabaseManager database = empty();
        execute(database, resource("/database/seed.sql"));
        return database;
    }

    public static void execute(DatabaseManager database, String sql) {
        try (Connection connection = database.connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void addProfiles(DatabaseManager database, String... names) {
        try (Connection connection = database.connect()) {
            for (String name : names) {
                Profile profile = Profile.of(name);
                insert(connection, "INSERT INTO profiles (id, name) VALUES (?, ?)", profile.id(), profile.name());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Inserts a small text work written as "Role: text" lines, with fresh UUIDs, and returns it the way
     * a catalog would.
     */
    public static TextWork addTextWork(DatabaseManager database, String title, String... lines) {
        String textWorkId = UUID.randomUUID().toString();
        Map<String, VoicePart> byName = new LinkedHashMap<>();
        List<TextWorkFragment> fragments = new ArrayList<>();
        for (String line : lines) {
            int colon = line.indexOf(':');
            String name = line.substring(0, colon).trim();
            VoicePart voicePart = byName.computeIfAbsent(name,
                    key -> new VoicePart(UUID.randomUUID().toString(), key));
            fragments.add(new TextWorkFragment(fragments.size() + 1, voicePart.id(),
                    line.substring(colon + 1).trim()));
        }
        try (Connection connection = database.connect()) {
            insert(connection, "INSERT INTO text_works (id, title, authors, language) VALUES (?, ?, ?, ?)",
                    UUID.fromString(textWorkId), title, "Тест", "rus");
            for (VoicePart voicePart : byName.values()) {
                insert(connection, "INSERT INTO voice_parts (id, text_work_id, name) VALUES (?, ?, ?)",
                        UUID.fromString(voicePart.id()), UUID.fromString(textWorkId), voicePart.name());
            }
            for (TextWorkFragment fragment : fragments) {
                insert(connection, "INSERT INTO text_work_fragments (id, text_work_id, number, voice_part_id, content)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        UUID.randomUUID(), UUID.fromString(textWorkId), fragment.number(),
                        UUID.fromString(fragment.voicePartId()), fragment.text());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        Map<String, VoicePart> byId = new LinkedHashMap<>();
        byName.values().forEach(voicePart -> byId.put(voicePart.id(), voicePart));
        return new TextWork(textWorkId, title, fragments, byId);
    }

    public static TextWork textWork(TextWorkCatalog catalog, String title) {
        return catalog.all().stream()
                .filter(textWork -> textWork.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет произведения " + title));
    }

    public static String voicePartId(TextWork textWork, String name) {
        return textWork.voicePartById().values().stream()
                .filter(voicePart -> voicePart.name().equals(name))
                .map(VoicePart::id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет роли " + name + " в " + textWork.title()));
    }

    private static void insert(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    private static List<String> statements(String script) {
        String withoutComments = script.lines()
                .filter(line -> !line.strip().startsWith("--"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split("(?m);\\s*$"))
                .map(String::strip)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private static String resource(String name) {
        try (InputStream stream = TestDatabase.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalStateException("Нет ресурса " + name + ": его копирует processTestResources");
            }
            return new String(stream.readAllBytes(), UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
