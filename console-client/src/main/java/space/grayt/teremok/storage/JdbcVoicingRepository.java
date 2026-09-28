package space.grayt.teremok.storage;

import static space.grayt.teremok.db.Jdbc.failure;
import static space.grayt.teremok.db.Jdbc.instant;
import static space.grayt.teremok.db.Jdbc.isConstraintViolation;
import static space.grayt.teremok.db.Jdbc.isForeignKeyViolation;
import static space.grayt.teremok.db.Jdbc.isUniqueViolation;
import static space.grayt.teremok.db.Jdbc.timestamp;
import static space.grayt.teremok.db.Jdbc.uuid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.exception.BusinessRuleException;
import space.grayt.teremok.exception.EntityNotFoundException;

/**
 * Voicings in PostgreSQL. The text work of a voicing comes from its voice part, the recorded
 * fragments from voicing_recordings. Upserts are an UPDATE and, when it touched nothing, an INSERT
 * inside one transaction: H2, which runs the tests, has no ON CONFLICT DO UPDATE.
 */
public final class JdbcVoicingRepository implements VoicingRepository {

    private static final String SELECT_VOICINGS = """
            SELECT v.id, p.text_work_id, v.voice_part_id, v.author_id, v.status, v.created_at
            FROM voicings v
            JOIN voice_parts p ON p.id = v.voice_part_id
            WHERE\s""";
    private static final String SELECT_RECORDED = """
            SELECT r.voicing_id, f.number
            FROM voicing_recordings r
            JOIN text_work_fragments f ON f.id = r.fragment_id
            JOIN voicings v ON v.id = r.voicing_id
            JOIN voice_parts p ON p.id = v.voice_part_id
            WHERE\s""";
    private static final String ORDER = " ORDER BY v.created_at, v.id";

    /** Publishes a draft only if every fragment of its voice part has a recording. */
    private static final String PUBLISH_READY_DRAFT = """
            UPDATE voicings SET status = 'PUBLISHED', published_at = COALESCE(published_at, ?)
            WHERE id = ? AND status = 'DRAFT'
              AND EXISTS (SELECT 1 FROM text_work_fragments f WHERE f.voice_part_id = voicings.voice_part_id)
              AND NOT EXISTS (
                  SELECT 1 FROM text_work_fragments f
                  WHERE f.voice_part_id = voicings.voice_part_id
                    AND NOT EXISTS (SELECT 1 FROM voicing_recordings r
                                    WHERE r.voicing_id = voicings.id AND r.fragment_id = f.id))""";

    private final DatabaseManager database;

    public JdbcVoicingRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public Optional<Voicing> find(long id) {
        return single("Не удалось прочитать озвучку " + id, "v.id = ?", id);
    }

    @Override
    public List<Voicing> findByTextWork(String textWorkId) {
        return list("Не удалось прочитать озвучки произведения", "p.text_work_id = ?", uuid(textWorkId));
    }

    @Override
    public List<Voicing> findByAuthor(String authorId) {
        return list("Не удалось прочитать озвучки автора " + authorId, "v.author_id = ?", authorId);
    }

    @Override
    public Optional<Voicing> findByVoicePartAndAuthor(String voicePartId, String authorId) {
        return single("Не удалось прочитать озвучку роли", "v.voice_part_id = ? AND v.author_id = ?",
                uuid(voicePartId), authorId);
    }

    @Override
    public Voicing insert(String voicePartId, String authorId, Instant createdAt) {
        String sql = "INSERT INTO voicings (voice_part_id, author_id, status, created_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql, new String[] {"id"})) {
            statement.setObject(1, uuid(voicePartId));
            statement.setString(2, authorId);
            statement.setString(3, VoicingStatus.DRAFT.name());
            statement.setObject(4, timestamp(createdAt));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return query(connection, "v.id = ?", id).get(0);
            }
        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                throw new BusinessRuleException("У автора уже есть озвучка этой роли.");
            }
            if (isForeignKeyViolation(e)) {
                throw new EntityNotFoundException("Не удалось создать озвучку: роли или профиля нет в базе данных.");
            }
            throw translate("Не удалось создать озвучку", e);
        }
    }

    @Override
    public void updateStatus(long id, VoicingStatus status, Instant changedAt) {
        String sql = status == VoicingStatus.PUBLISHED
                ? "UPDATE voicings SET status = 'PUBLISHED', published_at = COALESCE(published_at, ?) WHERE id = ?"
                : "UPDATE voicings SET status = ? WHERE id = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (status == VoicingStatus.PUBLISHED) {
                statement.setObject(1, timestamp(changedAt));
            } else {
                statement.setString(1, status.name());
            }
            statement.setLong(2, id);
            requireChanged(statement.executeUpdate(), id);
        } catch (SQLException e) {
            throw translate("Не удалось изменить статус озвучки " + id, e);
        }
    }

    /**
     * The transactional operation of «Опубликовать все готовые»: the UPDATE of each draft checks again
     * that it is complete, and if any of them is not, the drafts updated before it are rolled back.
     */
    @Override
    public void publishAll(List<Long> ids, Instant publishedAt) {
        if (ids.isEmpty()) {
            return;
        }
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(PUBLISH_READY_DRAFT)) {
                for (long id : ids) {
                    statement.setObject(1, timestamp(publishedAt));
                    statement.setLong(2, id);
                    if (statement.executeUpdate() != 1) {
                        throw new BusinessRuleException(
                                "Не все роли готовы к публикации, поэтому не опубликована ни одна.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                // Undoes the drafts already published by this loop.
                rollbackQuietly(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw translate("Не удалось опубликовать роли", e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM voicings WHERE id = ?")) {
            statement.setLong(1, id);
            requireChanged(statement.executeUpdate(), id);
        } catch (SQLException e) {
            throw translate("Не удалось удалить озвучку " + id, e);
        }
    }

    @Override
    public List<Vote> votes(long voicingId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT profile_id, kind FROM votes WHERE voicing_id = ? ORDER BY voted_at, profile_id")) {
            statement.setLong(1, voicingId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Vote> votes = new ArrayList<>();
                while (rows.next()) {
                    votes.add(new Vote(rows.getString("profile_id"), VoteKind.valueOf(rows.getString("kind"))));
                }
                return votes;
            }
        } catch (SQLException e) {
            throw failure("Не удалось прочитать голоса озвучки " + voicingId, e);
        }
    }

    @Override
    public void putVote(long voicingId, String profileId, VoteKind kind) {
        inTransaction("Не удалось сохранить голос", connection -> {
            int updated = update(connection,
                    "UPDATE votes SET kind = ?, voted_at = CURRENT_TIMESTAMP WHERE voicing_id = ? AND profile_id = ?",
                    kind.name(), voicingId, profileId);
            if (updated == 0) {
                update(connection, "INSERT INTO votes (voicing_id, profile_id, kind) VALUES (?, ?, ?)",
                        voicingId, profileId, kind.name());
            }
        });
    }

    @Override
    public void removeVote(long voicingId, String profileId) {
        try (Connection connection = database.connect()) {
            update(connection, "DELETE FROM votes WHERE voicing_id = ? AND profile_id = ?", voicingId, profileId);
        } catch (SQLException e) {
            throw translate("Не удалось снять голос", e);
        }
    }

    @Override
    public void markRecorded(long voicingId, int fragmentNumber, String audioPath, int durationMs) {
        inTransaction("Не удалось сохранить запись фрагмента " + fragmentNumber, connection -> {
            UUID fragmentId = fragmentOf(connection, voicingId, fragmentNumber);
            int updated = update(connection, "UPDATE voicing_recordings"
                            + " SET audio_path = ?, duration_ms = ?, recorded_at = CURRENT_TIMESTAMP"
                            + " WHERE voicing_id = ? AND fragment_id = ?",
                    audioPath, durationMs, voicingId, fragmentId);
            if (updated == 0) {
                update(connection, "INSERT INTO voicing_recordings (voicing_id, fragment_id, audio_path, duration_ms)"
                                + " VALUES (?, ?, ?, ?)",
                        voicingId, fragmentId, audioPath, durationMs);
            }
        });
    }

    @Override
    public Map<Integer, String> audioPaths(long voicingId) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT f.number, r.audio_path FROM voicing_recordings r"
                             + " JOIN text_work_fragments f ON f.id = r.fragment_id"
                             + " WHERE r.voicing_id = ?")) {
            statement.setLong(1, voicingId);
            try (ResultSet rows = statement.executeQuery()) {
                Map<Integer, String> paths = new TreeMap<>();
                while (rows.next()) {
                    paths.put(rows.getInt("number"), rows.getString("audio_path"));
                }
                return paths;
            }
        } catch (SQLException e) {
            throw failure("Не удалось прочитать записи озвучки " + voicingId, e);
        }
    }

    /** The fragment must belong to the voice part of the voicing, not only to its text work. */
    private static UUID fragmentOf(Connection connection, long voicingId, int fragmentNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT f.id FROM voicings v"
                        + " JOIN text_work_fragments f ON f.voice_part_id = v.voice_part_id"
                        + " WHERE v.id = ? AND f.number = ?")) {
            statement.setLong(1, voicingId);
            statement.setInt(2, fragmentNumber);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new EntityNotFoundException(
                            "У озвучки " + voicingId + " нет фрагмента " + fragmentNumber + ".");
                }
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private Optional<Voicing> single(String action, String condition, Object... parameters) {
        List<Voicing> found = list(action, condition, parameters);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private List<Voicing> list(String action, String condition, Object... parameters) {
        try (Connection connection = database.connect()) {
            return query(connection, condition, parameters);
        } catch (SQLException e) {
            throw failure(action, e);
        }
    }

    /**
     * Two queries with the same condition, voicings and then their recorded fragments, instead of one
     * query per voicing. The condition is a constant of this class; values are always parameters.
     */
    private static List<Voicing> query(Connection connection, String condition, Object... parameters)
            throws SQLException {
        Map<Long, Voicing> voicings = new LinkedHashMap<>();
        try (PreparedStatement statement = prepare(connection, SELECT_VOICINGS + condition + ORDER, parameters);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                Voicing voicing = new Voicing(
                        rows.getLong("id"),
                        rows.getString("text_work_id"),
                        rows.getString("voice_part_id"),
                        rows.getString("author_id"),
                        VoicingStatus.valueOf(rows.getString("status")),
                        instant(rows, "created_at"),
                        Set.of());
                voicings.put(voicing.id(), voicing);
            }
        }
        if (voicings.isEmpty()) {
            return List.of();
        }
        Map<Long, Set<Integer>> recorded = new HashMap<>();
        try (PreparedStatement statement = prepare(connection, SELECT_RECORDED + condition, parameters);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                recorded.computeIfAbsent(rows.getLong("voicing_id"), id -> new HashSet<>()).add(rows.getInt("number"));
            }
        }
        return voicings.values().stream()
                .map(voicing -> new Voicing(voicing.id(), voicing.textWorkId(), voicing.voicePartId(),
                        voicing.authorId(), voicing.status(), voicing.createdAt(),
                        recorded.getOrDefault(voicing.id(), Set.of())))
                .toList();
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return statement;
        } catch (SQLException e) {
            statement.close();
            throw e;
        }
    }

    private static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) {
            return statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }

    private void inTransaction(String action, SqlWork work) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw translate(action, e);
        }
    }

    /** A failed rollback must not hide the error that caused it. */
    private static void rollbackQuietly(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }

    private static void requireChanged(int rows, long id) {
        if (rows == 0) {
            throw new EntityNotFoundException("Озвучка " + id + " не найдена: возможно, её уже удалили.");
        }
    }

    /** Constraint violations are caller mistakes, not a broken database. */
    private static RuntimeException translate(String action, SQLException e) {
        if (isForeignKeyViolation(e)) {
            return new EntityNotFoundException(action + ": озвучки или профиля нет в базе данных.");
        }
        if (isConstraintViolation(e)) {
            return new IllegalArgumentException(action + ": " + e.getMessage(), e);
        }
        return failure(action, e);
    }
}
