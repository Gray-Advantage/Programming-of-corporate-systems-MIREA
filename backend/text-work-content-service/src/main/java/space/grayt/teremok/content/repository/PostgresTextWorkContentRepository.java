package space.grayt.teremok.content.repository;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.content.domain.TextWorkContent;
import space.grayt.teremok.events.TextWorkAddedEvent.SegmentPayload;
import space.grayt.teremok.events.TextWorkAddedEvent.VoicePartFragmentPayload;
import space.grayt.teremok.events.TextWorkAddedEvent.VoicePartPayload;

@Repository
public class PostgresTextWorkContentRepository implements TextWorkContentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresTextWorkContentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(TextWorkContent textWork) {
        jdbcTemplate.update(
                "INSERT INTO text_work_content (id) VALUES (?) ON CONFLICT (id) DO NOTHING",
                textWork.id());

        jdbcTemplate.update("DELETE FROM voice_part_fragment WHERE text_work_id = ?", textWork.id());
        jdbcTemplate.update("DELETE FROM segment WHERE text_work_id = ?", textWork.id());
        jdbcTemplate.update("DELETE FROM voice_part WHERE text_work_id = ?", textWork.id());

        var voicePartRows = new ArrayList<Object[]>(textWork.voiceParts().size());
        for (var index = 0; index < textWork.voiceParts().size(); index++) {
            var voicePart = textWork.voiceParts().get(index);
            voicePartRows.add(new Object[] {
                    voicePart.id(), textWork.id(), index, voicePart.name(), voicePart.totalFragmentsCount()
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO voice_part (id, text_work_id, voice_part_order, name, total_fragments_count)
                VALUES (?, ?, ?, ?, ?)
                """, voicePartRows);

        var segmentRows = new ArrayList<Object[]>(textWork.segments().size());
        var fragmentRows = new ArrayList<Object[]>();
        for (var segment : textWork.segments()) {
            segmentRows.add(new Object[] {
                    segment.id(), textWork.id(), segment.orderInTextWork(), segment.name()
            });
            for (var fragment : segment.fragments()) {
                fragmentRows.add(new Object[] {
                        fragment.id(),
                        textWork.id(),
                        segment.id(),
                        fragment.orderInSegment(),
                        fragment.content(),
                        fragment.voicePartId()
                });
            }
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO segment (id, text_work_id, segment_order, name)
                VALUES (?, ?, ?, ?)
                """, segmentRows);
        jdbcTemplate.batchUpdate("""
                INSERT INTO voice_part_fragment (
                    id, text_work_id, segment_id, fragment_order, content, voice_part_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, fragmentRows);
    }

    @Override
    public Optional<TextWorkContent> findById(UUID id) {
        var exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM text_work_content WHERE id = ?)",
                Boolean.class,
                id);
        if (!Boolean.TRUE.equals(exists)) {
            return Optional.empty();
        }

        var segments = jdbcTemplate.query("""
                        SELECT id, segment_order, name
                        FROM segment
                        WHERE text_work_id = ?
                        ORDER BY segment_order
                        """,
                (row, index) -> {
                    var segmentId = row.getObject("id", UUID.class);
                    var fragments = jdbcTemplate.query("""
                                    SELECT id, fragment_order, content, voice_part_id
                                    FROM voice_part_fragment
                                    WHERE segment_id = ?
                                    ORDER BY fragment_order
                                    """,
                            (fragmentRow, fragmentIndex) -> new VoicePartFragmentPayload(
                                    fragmentRow.getObject("id", UUID.class),
                                    fragmentRow.getInt("fragment_order"),
                                    fragmentRow.getString("content"),
                                    fragmentRow.getObject("voice_part_id", UUID.class)),
                            segmentId);
                    return new SegmentPayload(
                            segmentId,
                            row.getInt("segment_order"),
                            row.getString("name"),
                            fragments);
                },
                id);
        var voiceParts = jdbcTemplate.query("""
                        SELECT id, name, total_fragments_count
                        FROM voice_part
                        WHERE text_work_id = ?
                        ORDER BY voice_part_order
                        """,
                (row, index) -> new VoicePartPayload(
                        row.getObject("id", UUID.class),
                        row.getString("name"),
                        row.getInt("total_fragments_count")),
                id);
        return Optional.of(new TextWorkContent(id, segments, voiceParts));
    }

    @Override
    public long count() {
        var result = jdbcTemplate.queryForObject("SELECT count(*) FROM text_work_content", Long.class);
        return result == null ? 0 : result;
    }
}
