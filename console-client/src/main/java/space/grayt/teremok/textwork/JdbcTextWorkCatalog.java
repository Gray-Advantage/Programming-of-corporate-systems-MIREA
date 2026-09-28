package space.grayt.teremok.textwork;

import static space.grayt.teremok.db.Jdbc.failure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.VoicePart;

/**
 * Text works from the database. They do not change while the client runs, so they are read once,
 * on first use rather than in the constructor: App checks that the database is reachable first
 * and explains a stopped container instead of failing here.
 */
public final class JdbcTextWorkCatalog implements TextWorkCatalog {

    private final DatabaseManager database;
    private final List<String> warnings = new ArrayList<>();
    private Map<String, TextWork> textWorks;

    public JdbcTextWorkCatalog(DatabaseManager database) {
        this.database = database;
    }

    /** Ordered by title. */
    @Override
    public List<TextWork> all() {
        return List.copyOf(loaded().values());
    }

    @Override
    public Optional<TextWork> find(String textWorkId) {
        return Optional.ofNullable(loaded().get(textWorkId));
    }

    @Override
    public List<String> warnings() {
        loaded();
        return List.copyOf(warnings);
    }

    private Map<String, TextWork> loaded() {
        if (textWorks == null) {
            textWorks = load();
        }
        return textWorks;
    }

    private Map<String, TextWork> load() {
        try (Connection connection = database.connect()) {
            Map<String, String> titles = titles(connection);
            Map<String, Map<String, VoicePart>> voiceParts = voiceParts(connection);
            Map<String, List<TextWorkFragment>> fragments = fragments(connection);
            Map<String, TextWork> result = new LinkedHashMap<>();
            titles.forEach((id, title) -> textWork(id, title,
                    voiceParts.getOrDefault(id, Map.of()), fragments.getOrDefault(id, List.of()))
                    .ifPresent(textWork -> result.put(id, textWork)));
            return result;
        } catch (SQLException e) {
            throw failure("Не удалось загрузить произведения из базы данных", e);
        }
    }

    private Optional<TextWork> textWork(String id, String title, Map<String, VoicePart> voiceParts,
                                        List<TextWorkFragment> fragments) {
        if (fragments.isEmpty()) {
            warnings.add("Произведение «" + title + "» пропущено: в нём нет ни одного фрагмента.");
            return Optional.empty();
        }
        // The schema does not stop a fragment from naming a voice part of another text work.
        for (TextWorkFragment fragment : fragments) {
            if (!voiceParts.containsKey(fragment.voicePartId())) {
                warnings.add("Произведение «" + title + "» пропущено: фрагмент " + fragment.number()
                        + " ссылается на роль из другого произведения.");
                return Optional.empty();
            }
        }
        return Optional.of(new TextWork(id, title, fragments, voiceParts));
    }

    private static Map<String, String> titles(Connection connection) throws SQLException {
        Map<String, String> titles = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, title FROM text_works ORDER BY title, id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                titles.put(rows.getString("id"), rows.getString("title"));
            }
        }
        return titles;
    }

    private static Map<String, Map<String, VoicePart>> voiceParts(Connection connection) throws SQLException {
        Map<String, Map<String, VoicePart>> byTextWork = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, text_work_id, name FROM voice_parts");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                VoicePart voicePart = new VoicePart(rows.getString("id"), rows.getString("name"));
                byTextWork.computeIfAbsent(rows.getString("text_work_id"), key -> new LinkedHashMap<>())
                        .put(voicePart.id(), voicePart);
            }
        }
        return byTextWork;
    }

    private static Map<String, List<TextWorkFragment>> fragments(Connection connection) throws SQLException {
        Map<String, List<TextWorkFragment>> byTextWork = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT text_work_id, number, voice_part_id, content FROM text_work_fragments"
                        + " ORDER BY text_work_id, number");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                byTextWork.computeIfAbsent(rows.getString("text_work_id"), key -> new ArrayList<>())
                        .add(new TextWorkFragment(rows.getInt("number"), rows.getString("voice_part_id"),
                                rows.getString("content")));
            }
        }
        return byTextWork;
    }
}
