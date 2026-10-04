package space.grayt.teremok.catalog.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.catalog.domain.CatalogTextWork;
import space.grayt.teremok.events.TextWorkAddedEvent.OriginType;
import space.grayt.teremok.events.TextWorkAddedEvent.SegmentType;
import space.grayt.teremok.events.TextWorkAddedEvent.TextWorkOrigin;

@Repository
public class PostgresCatalogTextWorkRepository implements CatalogTextWorkRepository {

    private static final String SELECT_TEXT_WORK = """
            SELECT id, name, publication_date, language, origin_type, translated_from,
                   segments_count, segment_type
            FROM catalog_text_work
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresCatalogTextWorkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(CatalogTextWork textWork) {
        jdbcTemplate.update("""
                        INSERT INTO catalog_text_work (
                            id, name, publication_date, language, origin_type, translated_from,
                            segments_count, segment_type
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                            name = EXCLUDED.name,
                            publication_date = EXCLUDED.publication_date,
                            language = EXCLUDED.language,
                            origin_type = EXCLUDED.origin_type,
                            translated_from = EXCLUDED.translated_from,
                            segments_count = EXCLUDED.segments_count,
                            segment_type = EXCLUDED.segment_type
                        """,
                textWork.id(),
                textWork.name(),
                textWork.publicationDate(),
                textWork.language(),
                textWork.origin().type().name(),
                textWork.origin().translatedFrom(),
                textWork.segmentsCount(),
                textWork.segmentType().name());

        jdbcTemplate.update("DELETE FROM catalog_text_work_author WHERE text_work_id = ?", textWork.id());
        jdbcTemplate.update("DELETE FROM catalog_text_work_translator WHERE text_work_id = ?", textWork.id());

        var authors = new ArrayList<Object[]>(textWork.authors().size());
        for (var index = 0; index < textWork.authors().size(); index++) {
            authors.add(new Object[] {textWork.id(), index, textWork.authors().get(index)});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO catalog_text_work_author (text_work_id, author_order, author)
                VALUES (?, ?, ?)
                """, authors);

        var translators = textWork.origin().translators();
        var translatorRows = new ArrayList<Object[]>(translators.size());
        for (var index = 0; index < translators.size(); index++) {
            translatorRows.add(new Object[] {textWork.id(), index, translators.get(index)});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO catalog_text_work_translator (text_work_id, translator_order, translator)
                VALUES (?, ?, ?)
                """, translatorRows);
    }

    @Override
    public List<CatalogTextWork> findAll() {
        return jdbcTemplate.query(SELECT_TEXT_WORK + " ORDER BY lower(name), id", this::mapTextWork);
    }

    @Override
    public Optional<CatalogTextWork> findById(UUID id) {
        return jdbcTemplate.query(SELECT_TEXT_WORK + " WHERE id = ?", this::mapTextWork, id)
                .stream()
                .findFirst();
    }

    @Override
    public long count() {
        var result = jdbcTemplate.queryForObject("SELECT count(*) FROM catalog_text_work", Long.class);
        return result == null ? 0 : result;
    }

    private CatalogTextWork mapTextWork(ResultSet resultSet, int rowNumber) throws SQLException {
        var id = resultSet.getObject("id", UUID.class);
        var authors = jdbcTemplate.query(
                """
                SELECT author
                FROM catalog_text_work_author
                WHERE text_work_id = ?
                ORDER BY author_order
                """,
                (row, index) -> row.getString("author"),
                id);
        var translators = jdbcTemplate.query(
                """
                SELECT translator
                FROM catalog_text_work_translator
                WHERE text_work_id = ?
                ORDER BY translator_order
                """,
                (row, index) -> row.getString("translator"),
                id);
        var origin = new TextWorkOrigin(
                OriginType.valueOf(resultSet.getString("origin_type")),
                resultSet.getString("translated_from"),
                translators);

        return new CatalogTextWork(
                id,
                authors,
                resultSet.getString("name"),
                resultSet.getObject("publication_date", LocalDate.class),
                resultSet.getString("language"),
                origin,
                resultSet.getInt("segments_count"),
                SegmentType.valueOf(resultSet.getString("segment_type")));
    }
}
