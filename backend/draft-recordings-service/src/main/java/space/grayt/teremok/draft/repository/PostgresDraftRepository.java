package space.grayt.teremok.draft.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.draft.domain.DraftRecording;
import space.grayt.teremok.draft.domain.RoleFragment;
import space.grayt.teremok.draft.domain.RoleProjection;
import space.grayt.teremok.events.TextWorkAddedEvent;

@Repository
public class PostgresDraftRepository implements DraftRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void saveTextWork(TextWorkAddedEvent event) {
        var textWork = event.textWork();
        jdbcTemplate.update("INSERT INTO draft_text_work (id) VALUES (?) ON CONFLICT (id) DO NOTHING", textWork.id());
        for (var role : textWork.voiceParts()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO draft_role (id, text_work_id, name) VALUES (?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET text_work_id = EXCLUDED.text_work_id, name = EXCLUDED.name
                    """,
                    role.id(), textWork.id(), role.name());
        }
        for (var segment : textWork.segments()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO draft_segment (id, text_work_id, order_in_text_work) VALUES (?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        text_work_id = EXCLUDED.text_work_id,
                        order_in_text_work = EXCLUDED.order_in_text_work
                    """,
                    segment.id(), textWork.id(), segment.orderInTextWork());
            for (var fragment : segment.fragments()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO draft_fragment (id, segment_id, role_id, order_in_segment)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                            segment_id = EXCLUDED.segment_id,
                            role_id = EXCLUDED.role_id,
                            order_in_segment = EXCLUDED.order_in_segment
                        """,
                        fragment.id(), segment.id(), fragment.voicePartId(), fragment.orderInSegment());
            }
        }
    }

    @Override
    public void saveUser(UUID userId) {
        jdbcTemplate.update("INSERT INTO draft_user (id) VALUES (?) ON CONFLICT (id) DO NOTHING", userId);
    }

    @Override
    public boolean userExists(UUID userId) {
        return count("SELECT count(*) FROM draft_user WHERE id = ?", userId) > 0;
    }

    @Override
    public boolean fragmentExists(UUID fragmentId) {
        return count("SELECT count(*) FROM draft_fragment WHERE id = ?", fragmentId) > 0;
    }

    @Override
    public Optional<RoleProjection> findRole(UUID roleId) {
        return jdbcTemplate.query(
                        "SELECT id, text_work_id, name FROM draft_role WHERE id = ?",
                        (row, index) -> new RoleProjection(
                                row.getObject("id", UUID.class),
                                row.getObject("text_work_id", UUID.class),
                                row.getString("name")),
                        roleId)
                .stream()
                .findFirst();
    }

    @Override
    public List<RoleFragment> findRoleFragments(UUID roleId) {
        return jdbcTemplate.query(
                """
                SELECT f.id, f.segment_id, s.order_in_text_work, f.order_in_segment
                FROM draft_fragment f
                JOIN draft_segment s ON s.id = f.segment_id
                WHERE f.role_id = ?
                ORDER BY s.order_in_text_work, f.order_in_segment
                """,
                (row, index) -> new RoleFragment(
                        row.getObject("id", UUID.class),
                        row.getObject("segment_id", UUID.class),
                        row.getInt("order_in_text_work"),
                        row.getInt("order_in_segment")),
                roleId);
    }

    @Override
    public void saveDraft(DraftRecording recording) {
        jdbcTemplate.update(
                """
                INSERT INTO draft_fragment_recording (
                    id, user_id, fragment_id, object_key, original_file_name,
                    content_type, size_bytes, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                recording.id(), recording.userId(), recording.fragmentId(), recording.objectKey(),
                recording.originalFileName(), recording.contentType(), recording.sizeBytes(),
                recording.createdAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public Optional<DraftRecording> findDraft(UUID recordingId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, user_id, fragment_id, object_key, original_file_name,
                               content_type, size_bytes, created_at
                        FROM draft_fragment_recording
                        WHERE id = ?
                        """,
                        this::mapDraft,
                        recordingId)
                .stream()
                .findFirst();
    }

    @Override
    public List<DraftRecording> findUserRoleDrafts(UUID userId, UUID roleId) {
        return jdbcTemplate.query(
                """
                SELECT r.id, r.user_id, r.fragment_id, r.object_key, r.original_file_name,
                       r.content_type, r.size_bytes, r.created_at
                FROM draft_fragment_recording r
                JOIN draft_fragment f ON f.id = r.fragment_id
                WHERE r.user_id = ? AND f.role_id = ?
                ORDER BY r.created_at, r.id
                """,
                this::mapDraft,
                userId,
                roleId);
    }

    @Override
    public void deleteDrafts(List<UUID> recordingIds) {
        for (var id : recordingIds) {
            jdbcTemplate.update("DELETE FROM draft_fragment_recording WHERE id = ?", id);
        }
    }

    @Override
    public void savePublication(UUID id, UUID userId, UUID textWorkId, UUID roleId, Instant publishedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO draft_role_publication (id, user_id, text_work_id, role_id, published_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                id, userId, textWorkId, roleId, publishedAt.atOffset(ZoneOffset.UTC));
    }

    private DraftRecording mapDraft(ResultSet row, int index) throws SQLException {
        return new DraftRecording(
                row.getObject("id", UUID.class),
                row.getObject("user_id", UUID.class),
                row.getObject("fragment_id", UUID.class),
                row.getString("object_key"),
                row.getString("original_file_name"),
                row.getString("content_type"),
                row.getLong("size_bytes"),
                row.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private long count(String sql, UUID id) {
        var result = jdbcTemplate.queryForObject(sql, Long.class, id);
        return result == null ? 0 : result;
    }
}
