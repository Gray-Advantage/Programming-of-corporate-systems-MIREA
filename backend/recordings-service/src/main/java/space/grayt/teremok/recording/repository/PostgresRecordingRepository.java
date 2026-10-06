package space.grayt.teremok.recording.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.events.RenderCompletedEvent;
import space.grayt.teremok.events.RoleRecordingPublishedEvent;
import space.grayt.teremok.events.TextWorkAddedEvent;
import space.grayt.teremok.recording.domain.PublishedRoleRecording;
import space.grayt.teremok.recording.domain.RecordingRole;
import space.grayt.teremok.recording.domain.RenderFragment;
import space.grayt.teremok.recording.domain.RenderJob;
import space.grayt.teremok.recording.domain.RenderOutput;

@Repository
public class PostgresRecordingRepository implements RecordingRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresRecordingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void saveTextWork(TextWorkAddedEvent event) {
        var textWork = event.textWork();
        jdbcTemplate.update(
                "INSERT INTO recording_text_work (id) VALUES (?) ON CONFLICT (id) DO NOTHING",
                textWork.id());
        for (var role : textWork.voiceParts()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO recording_role (id, text_work_id, name) VALUES (?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET text_work_id = EXCLUDED.text_work_id, name = EXCLUDED.name
                    """,
                    role.id(), textWork.id(), role.name());
        }
        for (var segment : textWork.segments()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO recording_segment (id, text_work_id, order_in_text_work) VALUES (?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        text_work_id = EXCLUDED.text_work_id,
                        order_in_text_work = EXCLUDED.order_in_text_work
                    """,
                    segment.id(), textWork.id(), segment.orderInTextWork());
            for (var fragment : segment.fragments()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO recording_fragment (id, segment_id, role_id, order_in_segment)
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
        jdbcTemplate.update("INSERT INTO recording_user (id) VALUES (?) ON CONFLICT (id) DO NOTHING", userId);
    }

    @Override
    @Transactional
    public void savePublishedRole(RoleRecordingPublishedEvent event) {
        var recording = event.roleRecording();
        jdbcTemplate.update(
                """
                INSERT INTO role_recording (id, user_id, text_work_id, role_id, published_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    text_work_id = EXCLUDED.text_work_id,
                    role_id = EXCLUDED.role_id,
                    published_at = EXCLUDED.published_at
                """,
                recording.id(), recording.userId(), recording.textWorkId(), recording.roleId(),
                recording.publishedAt().atOffset(ZoneOffset.UTC));
        for (var fragment : recording.fragments()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO fragment_recording (
                        id, role_recording_id, fragment_id, object_key, content_type, size_bytes
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        role_recording_id = EXCLUDED.role_recording_id,
                        fragment_id = EXCLUDED.fragment_id,
                        object_key = EXCLUDED.object_key,
                        content_type = EXCLUDED.content_type,
                        size_bytes = EXCLUDED.size_bytes
                    """,
                    fragment.id(), recording.id(), fragment.fragmentId(), fragment.objectKey(),
                    fragment.contentType(), fragment.sizeBytes());
        }
    }

    @Override
    public boolean userExists(UUID userId) {
        var result = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM recording_user WHERE id = ?", Long.class, userId);
        return result != null && result > 0;
    }

    @Override
    public List<RecordingRole> findRoles(UUID textWorkId) {
        return jdbcTemplate.query(
                "SELECT id, text_work_id, name FROM recording_role WHERE text_work_id = ? ORDER BY lower(name), id",
                (row, index) -> new RecordingRole(
                        row.getObject("id", UUID.class),
                        row.getObject("text_work_id", UUID.class),
                        row.getString("name")),
                textWorkId);
    }

    @Override
    public List<PublishedRoleRecording> findRoleRecordings(UUID roleId) {
        return jdbcTemplate.query(
                """
                SELECT id, user_id, text_work_id, role_id, published_at
                FROM role_recording
                WHERE role_id = ?
                ORDER BY published_at DESC, id
                """,
                this::mapRoleRecording,
                roleId);
    }

    @Override
    public Optional<PublishedRoleRecording> findRoleRecording(UUID id) {
        return jdbcTemplate.query(
                        """
                        SELECT id, user_id, text_work_id, role_id, published_at
                        FROM role_recording
                        WHERE id = ?
                        """,
                        this::mapRoleRecording,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public List<RenderFragment> findRenderFragments(UUID roleRecordingId) {
        return jdbcTemplate.query(
                """
                SELECT s.id AS segment_id, s.order_in_text_work,
                       f.id AS fragment_id, f.order_in_segment, fr.object_key
                FROM fragment_recording fr
                JOIN recording_fragment f ON f.id = fr.fragment_id
                JOIN recording_segment s ON s.id = f.segment_id
                WHERE fr.role_recording_id = ?
                ORDER BY s.order_in_text_work, f.order_in_segment
                """,
                (row, index) -> new RenderFragment(
                        row.getObject("segment_id", UUID.class),
                        row.getInt("order_in_text_work"),
                        row.getObject("fragment_id", UUID.class),
                        row.getInt("order_in_segment"),
                        row.getString("object_key")),
                roleRecordingId);
    }

    @Override
    public long countTextWorkFragments(UUID textWorkId) {
        var result = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM recording_fragment f
                JOIN recording_segment s ON s.id = f.segment_id
                WHERE s.text_work_id = ?
                """,
                Long.class,
                textWorkId);
        return result == null ? 0 : result;
    }

    @Override
    @Transactional
    public void saveRenderJob(RenderJob job, Map<UUID, UUID> roleSelections) {
        jdbcTemplate.update(
                """
                INSERT INTO render_job (
                    id, user_id, text_work_id, output_mode, status, created_at, completed_at, error
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                job.id(), job.userId(), job.textWorkId(), job.outputMode(), job.status(),
                job.createdAt().atOffset(ZoneOffset.UTC), null, null);
        for (var selection : roleSelections.entrySet()) {
            jdbcTemplate.update(
                    "INSERT INTO render_job_role (render_job_id, role_id, role_recording_id) VALUES (?, ?, ?)",
                    job.id(), selection.getKey(), selection.getValue());
        }
    }

    @Override
    public Optional<RenderJob> findRenderJob(UUID id, UUID userId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, user_id, text_work_id, output_mode, status, created_at, completed_at, error
                        FROM render_job
                        WHERE id = ? AND user_id = ?
                        """,
                        this::mapRenderJob,
                        id,
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public List<RenderOutput> findRenderOutputs(UUID renderJobId) {
        return jdbcTemplate.query(
                """
                SELECT segment_id, object_key, content_type
                FROM render_output
                WHERE render_job_id = ?
                ORDER BY segment_id NULLS FIRST, object_key
                """,
                (row, index) -> new RenderOutput(
                        row.getObject("segment_id", UUID.class),
                        row.getString("object_key"),
                        row.getString("content_type")),
                renderJobId);
    }

    @Override
    @Transactional
    public void completeRender(RenderCompletedEvent event, Instant completedAt) {
        var result = event.renderResult();
        jdbcTemplate.update(
                """
                UPDATE render_job
                SET status = ?, completed_at = ?, error = ?
                WHERE id = ?
                """,
                result.status().name(), completedAt.atOffset(ZoneOffset.UTC), result.error(), result.renderId());
        jdbcTemplate.update("DELETE FROM render_output WHERE render_job_id = ?", result.renderId());
        for (var output : result.outputs()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO render_output (id, render_job_id, segment_id, object_key, content_type)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), result.renderId(), output.segmentId(), output.objectKey(), output.contentType());
        }
    }

    private PublishedRoleRecording mapRoleRecording(ResultSet row, int index) throws SQLException {
        return new PublishedRoleRecording(
                row.getObject("id", UUID.class),
                row.getObject("user_id", UUID.class),
                row.getObject("text_work_id", UUID.class),
                row.getObject("role_id", UUID.class),
                row.getObject("published_at", OffsetDateTime.class).toInstant());
    }

    private RenderJob mapRenderJob(ResultSet row, int index) throws SQLException {
        var completedAt = row.getObject("completed_at", OffsetDateTime.class);
        return new RenderJob(
                row.getObject("id", UUID.class),
                row.getObject("user_id", UUID.class),
                row.getObject("text_work_id", UUID.class),
                row.getString("output_mode"),
                row.getString("status"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                completedAt == null ? null : completedAt.toInstant(),
                row.getString("error"));
    }
}
