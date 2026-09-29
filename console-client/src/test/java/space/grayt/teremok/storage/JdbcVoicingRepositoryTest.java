package space.grayt.teremok.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.db.TestDatabase;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.exception.BusinessRuleException;
import space.grayt.teremok.exception.EntityNotFoundException;

class JdbcVoicingRepositoryTest {

    private static final Instant CREATED = Instant.parse("2026-09-07T12:00:00Z");
    private static final Instant PUBLISHED = Instant.parse("2026-09-08T09:30:00Z");

    private DatabaseManager database;
    private VoicingRepository repository;
    private TextWork book;
    private String wolf;
    private String hood;

    @BeforeEach
    void setUp() {
        database = TestDatabase.empty();
        repository = new JdbcVoicingRepository(database);
        book = TestDatabase.addTextWork(database, "Красная Шапочка",
                "Волк: Куда ты идёшь?",
                "Шапочка: К бабушке.",
                "Волк: А где живёт бабушка?");
        wolf = TestDatabase.voicePartId(book, "Волк");
        hood = TestDatabase.voicePartId(book, "Шапочка");
        TestDatabase.addProfiles(database, "sergey", "masha", "petya");
    }

    private Voicing insertWolf(String author) {
        return repository.insert(wolf, author, CREATED);
    }

    private Voicing completeWolf(String author) {
        Voicing voicing = insertWolf(author);
        repository.markRecorded(voicing.id(), 1, voicing.id() + "/fragment-0001.wav", 1000);
        repository.markRecorded(voicing.id(), 3, voicing.id() + "/fragment-0003.wav", 1000);
        return voicing;
    }

    private Instant publishedAt(long id) throws Exception {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT published_at FROM voicings WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                OffsetDateTime value = row.getObject(1, OffsetDateTime.class);
                return value == null ? null : value.toInstant();
            }
        }
    }

    private int recordingRows(long id) throws Exception {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM voicing_recordings WHERE voicing_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    @Test
    void insertedDraftGetsGeneratedIdAndTextWorkFromItsVoicePart() {
        Voicing inserted = insertWolf("sergey");
        Voicing loaded = repository.find(inserted.id()).orElseThrow();

        assertTrue(inserted.id() > 0);
        assertEquals(inserted, loaded);
        assertEquals(book.id(), loaded.textWorkId());
        assertEquals(wolf, loaded.voicePartId());
        assertEquals("sergey", loaded.authorId());
        assertEquals(VoicingStatus.DRAFT, loaded.status());
        assertEquals(CREATED, loaded.createdAt());
        assertEquals(Set.of(), loaded.recordedFragments());
    }

    @Test
    void idsAreGeneratedByTheDatabase() {
        assertNotEquals(insertWolf("sergey").id(), insertWolf("masha").id());
    }

    @Test
    void missingVoicingIsNotFound() {
        assertTrue(repository.find(999L).isEmpty());
    }

    @Test
    void secondVoicingOfSameVoicePartByAuthorIsRejected() {
        insertWolf("sergey");

        assertThrows(BusinessRuleException.class, () -> insertWolf("sergey"));
        assertEquals(1, repository.findByAuthor("sergey").size());
    }

    @Test
    void unknownAuthorOrVoicePartIsRejected() {
        assertThrows(EntityNotFoundException.class, () -> insertWolf("никто"));
        assertThrows(EntityNotFoundException.class,
                () -> repository.insert("00000000-0000-0000-0000-000000000000", "sergey", CREATED));
    }

    @Test
    void findsByTextWorkAuthorAndVoicePart() {
        Voicing sergeyWolf = insertWolf("sergey");
        Voicing mashaHood = repository.insert(hood, "masha", CREATED);
        TextWork other = TestDatabase.addTextWork(database, "Репка", "Дед: Тянем-потянем.");
        repository.insert(TestDatabase.voicePartId(other, "Дед"), "masha", CREATED);

        assertEquals(List.of(sergeyWolf.id(), mashaHood.id()),
                repository.findByTextWork(book.id()).stream().map(Voicing::id).toList());
        assertEquals(2, repository.findByAuthor("masha").size());
        assertEquals(sergeyWolf.id(), repository.findByVoicePartAndAuthor(wolf, "sergey").orElseThrow().id());
        assertTrue(repository.findByVoicePartAndAuthor(hood, "sergey").isEmpty());
    }

    @Test
    void listsAreInOrderOfCreation() {
        Voicing later = repository.insert(hood, "sergey", CREATED.plusSeconds(60));
        Voicing earlier = insertWolf("sergey");

        assertEquals(List.of(earlier.id(), later.id()),
                repository.findByAuthor("sergey").stream().map(Voicing::id).toList());
    }

    @Test
    void recordedFragmentsComeFromRecordings() {
        Voicing voicing = insertWolf("sergey");
        Voicing other = insertWolf("masha");

        repository.markRecorded(voicing.id(), 3, voicing.id() + "/fragment-0003.wav", 2500);
        repository.markRecorded(other.id(), 1, other.id() + "/fragment-0001.wav", 900);

        assertEquals(Set.of(3), repository.find(voicing.id()).orElseThrow().recordedFragments());
        assertEquals(Set.of(3), repository.findByAuthor("sergey").get(0).recordedFragments());
        assertEquals(Map.of(3, voicing.id() + "/fragment-0003.wav"), repository.audioPaths(voicing.id()));
    }

    @Test
    void recordingAgainReplacesTheRow() throws Exception {
        Voicing voicing = insertWolf("sergey");

        repository.markRecorded(voicing.id(), 1, "a.wav", 1000);
        repository.markRecorded(voicing.id(), 1, "b.wav", 2000);

        assertEquals(1, recordingRows(voicing.id()));
        assertEquals(Map.of(1, "b.wav"), repository.audioPaths(voicing.id()));
    }

    @Test
    void audioPathsAreOrderedByFragment() {
        Voicing voicing = insertWolf("sergey");
        repository.markRecorded(voicing.id(), 3, "c.wav", 1000);
        repository.markRecorded(voicing.id(), 1, "a.wav", 1000);

        assertEquals(List.of(1, 3), List.copyOf(repository.audioPaths(voicing.id()).keySet()));
    }

    @Test
    void fragmentOfAnotherVoicePartCannotBeRecorded() {
        Voicing voicing = insertWolf("sergey");

        assertThrows(EntityNotFoundException.class, () -> repository.markRecorded(voicing.id(), 2, "x.wav", 1000));
        assertThrows(EntityNotFoundException.class, () -> repository.markRecorded(voicing.id(), 99, "x.wav", 1000));
        assertTrue(repository.audioPaths(voicing.id()).isEmpty());
    }

    @Test
    void durationOutsideSchemaLimitsIsRejected() {
        Voicing voicing = insertWolf("sergey");

        assertThrows(IllegalArgumentException.class, () -> repository.markRecorded(voicing.id(), 1, "x.wav", -1));
        assertThrows(IllegalArgumentException.class,
                () -> repository.markRecorded(voicing.id(), 1, "x.wav", 120_001));
        assertTrue(repository.audioPaths(voicing.id()).isEmpty());
    }

    @Test
    void firstPublicationSetsPublishedAtAndRepublishingKeepsIt() throws Exception {
        Voicing voicing = completeWolf("sergey");
        assertNull(publishedAt(voicing.id()));

        repository.updateStatus(voicing.id(), VoicingStatus.PUBLISHED, PUBLISHED);
        repository.updateStatus(voicing.id(), VoicingStatus.ARCHIVED, PUBLISHED.plusSeconds(60));
        repository.updateStatus(voicing.id(), VoicingStatus.PUBLISHED, PUBLISHED.plusSeconds(120));

        assertEquals(VoicingStatus.PUBLISHED, repository.find(voicing.id()).orElseThrow().status());
        assertEquals(PUBLISHED, publishedAt(voicing.id()));
    }

    /** The schema requires published_at for every status but DRAFT. */
    @Test
    void neverPublishedVoicingCannotBeArchivedInDatabase() {
        Voicing voicing = insertWolf("sergey");

        assertThrows(IllegalArgumentException.class,
                () -> repository.updateStatus(voicing.id(), VoicingStatus.ARCHIVED, PUBLISHED));
        assertEquals(VoicingStatus.DRAFT, repository.find(voicing.id()).orElseThrow().status());
    }

    @Test
    void changingMissingVoicingIsNotFound() {
        assertThrows(EntityNotFoundException.class,
                () -> repository.updateStatus(999L, VoicingStatus.PUBLISHED, PUBLISHED));
        assertThrows(EntityNotFoundException.class, () -> repository.delete(999L));
        assertThrows(EntityNotFoundException.class, () -> repository.markRecorded(999L, 1, "x.wav", 1000));
    }

    @Test
    void deleteRemovesRecordingsAndVotesToo() throws Exception {
        Voicing voicing = completeWolf("sergey");
        repository.updateStatus(voicing.id(), VoicingStatus.PUBLISHED, PUBLISHED);
        repository.putVote(voicing.id(), "masha", VoteKind.LIKE);
        repository.updateStatus(voicing.id(), VoicingStatus.ARCHIVED, PUBLISHED);

        repository.delete(voicing.id());

        assertTrue(repository.find(voicing.id()).isEmpty());
        assertEquals(0, recordingRows(voicing.id()));
        assertEquals(List.of(), repository.votes(voicing.id()));
    }

    @Test
    void votesAreSavedReplacedAndRemoved() {
        Voicing voicing = insertWolf("sergey");

        repository.putVote(voicing.id(), "masha", VoteKind.LIKE);
        assertEquals(List.of(new Vote("masha", VoteKind.LIKE)), repository.votes(voicing.id()));

        repository.putVote(voicing.id(), "masha", VoteKind.DISLIKE);
        assertEquals(List.of(new Vote("masha", VoteKind.DISLIKE)), repository.votes(voicing.id()));

        repository.removeVote(voicing.id(), "masha");
        assertEquals(List.of(), repository.votes(voicing.id()));
    }

    @Test
    void voteOfUnknownProfileIsRejected() {
        Voicing voicing = insertWolf("sergey");

        assertThrows(EntityNotFoundException.class, () -> repository.putVote(voicing.id(), "никто", VoteKind.LIKE));
        assertThrows(EntityNotFoundException.class, () -> repository.putVote(999L, "masha", VoteKind.LIKE));
    }

    @Test
    void publishAllPublishesEveryCompleteDraft() throws Exception {
        Voicing first = completeWolf("sergey");
        Voicing second = completeWolf("masha");

        repository.publishAll(List.of(first.id(), second.id()), PUBLISHED);

        assertEquals(VoicingStatus.PUBLISHED, repository.find(first.id()).orElseThrow().status());
        assertEquals(VoicingStatus.PUBLISHED, repository.find(second.id()).orElseThrow().status());
        assertEquals(PUBLISHED, publishedAt(second.id()));
    }

    /** The first UPDATE succeeds, the second finds a missing fragment: the first must be rolled back. */
    @Test
    void publishAllRollsBackWhenOneDraftIsNotComplete() throws Exception {
        Voicing complete = completeWolf("sergey");
        Voicing incomplete = insertWolf("masha");
        repository.markRecorded(incomplete.id(), 1, incomplete.id() + "/fragment-0001.wav", 1000);

        assertThrows(BusinessRuleException.class,
                () -> repository.publishAll(List.of(complete.id(), incomplete.id()), PUBLISHED));

        assertEquals(VoicingStatus.DRAFT, repository.find(complete.id()).orElseThrow().status());
        assertEquals(VoicingStatus.DRAFT, repository.find(incomplete.id()).orElseThrow().status());
        assertNull(publishedAt(complete.id()));
    }

    @Test
    void publishAllRollsBackWhenOneIsNoLongerDraft() {
        Voicing draft = completeWolf("sergey");
        Voicing archived = completeWolf("masha");
        repository.updateStatus(archived.id(), VoicingStatus.PUBLISHED, PUBLISHED);
        repository.updateStatus(archived.id(), VoicingStatus.ARCHIVED, PUBLISHED);

        assertThrows(BusinessRuleException.class,
                () -> repository.publishAll(List.of(draft.id(), archived.id(), 999L), PUBLISHED));

        assertEquals(VoicingStatus.DRAFT, repository.find(draft.id()).orElseThrow().status());
        assertEquals(VoicingStatus.ARCHIVED, repository.find(archived.id()).orElseThrow().status());
    }

    @Test
    void publishAllOfNothingDoesNothing() {
        assertDoesNotThrow(() -> repository.publishAll(List.of(), PUBLISHED));
    }

    @Test
    void sqlFailureBecomesStorageException() {
        Voicing voicing = insertWolf("sergey");
        TestDatabase.execute(database, "DROP TABLE votes");

        StorageException error = assertThrows(StorageException.class, () -> repository.votes(voicing.id()));

        assertTrue(error.getMessage().startsWith("Не удалось прочитать голоса"), error::getMessage);
    }

    @Test
    void lostDatabaseBecomesStorageException() {
        VoicingRepository unreachable = new JdbcVoicingRepository(new DatabaseManager(
                new space.grayt.teremok.db.DatabaseConfig("jdbc:postgresql://localhost:1/teremok", "t", "t")));

        assertThrows(StorageException.class, () -> unreachable.findByAuthor("sergey"));
        assertThrows(StorageException.class, () -> unreachable.putVote(1L, "sergey", VoteKind.LIKE));
    }
}
