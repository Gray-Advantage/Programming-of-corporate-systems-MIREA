package space.grayt.teremok.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.Fixture;
import space.grayt.teremok.domain.RatedVoicing;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.exception.EntityNotFoundException;

class VotingServiceTest {

    private Fixture fixture;
    private TextWork book;
    private String wolf;
    private VotingService voting;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        fixture = Fixture.empty(dir);
        book = fixture.addTextWork("Красная Шапочка", "Волк: Куда ты идёшь?", "Шапочка: К бабушке.");
        wolf = fixture.voicePartId(book, "Волк");
        fixture.profile("masha");
        voting = fixture.voting();
    }

    private Voicing published(String author, String createdAt) {
        return fixture.withStatus(fixture.draft(book, "Волк", author, Instant.parse(createdAt)),
                VoicingStatus.PUBLISHED);
    }

    private void dislikes(Voicing voicing, int count) {
        for (int i = 0; i < count; i++) {
            fixture.vote(voicing, VoteKind.DISLIKE, "hater" + i);
        }
    }

    @Test
    void scoreIsLikesMinusDislikes() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");
        fixture.likes(voicing, 5);
        dislikes(voicing, 2);

        RatedVoicing rated = voting.rate(voicing);

        assertEquals(5, rated.likes());
        assertEquals(2, rated.dislikes());
        assertEquals(3, rated.score());
    }

    @Test
    void sortsByScoreDescending() {
        Voicing weak = published("weak", "2026-09-01T10:00:00Z");
        Voicing strong = published("strong", "2026-09-01T10:00:00Z");
        fixture.likes(weak, 1);
        fixture.likes(strong, 4);

        List<RatedVoicing> ranked = voting.ranked(book.id(), wolf);

        assertEquals("strong", ranked.get(0).voicing().authorId());
        assertEquals("weak", ranked.get(1).voicing().authorId());
    }

    @Test
    void onEqualScoreMoreLikesRanksHigher() {
        Voicing quiet = published("quiet", "2026-09-01T10:00:00Z");
        Voicing loud = published("loud", "2026-09-01T10:00:00Z");
        fixture.likes(quiet, 1);
        fixture.likes(loud, 5);
        dislikes(loud, 4);

        assertEquals("loud", voting.ranked(book.id(), wolf).get(0).voicing().authorId());
    }

    @Test
    void onFullTieNewerRanksHigher() {
        published("old", "2026-09-01T10:00:00Z");
        published("new", "2026-09-05T10:00:00Z");

        assertEquals("new", voting.ranked(book.id(), wolf).get(0).voicing().authorId());
    }

    @Test
    void draftsAndArchivedAreNotRanked() {
        fixture.draft(book, "Волк", "draft");
        fixture.withStatus(fixture.draft(book, "Волк", "archived"), VoicingStatus.ARCHIVED);
        published("ready", "2026-09-01T10:00:00Z");

        List<RatedVoicing> ranked = voting.ranked(book.id(), wolf);

        assertEquals(1, ranked.size());
        assertEquals("ready", ranked.get(0).voicing().authorId());
    }

    @Test
    void firstVoteIsAdded() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");

        assertEquals(VoteResult.ADDED, voting.vote(voicing.id(), "masha", VoteKind.LIKE));
        assertEquals(VoteKind.LIKE, voting.voteOf(voicing.id(), "masha").orElseThrow());
    }

    @Test
    void repeatingSameVoteRemovesIt() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");
        voting.vote(voicing.id(), "masha", VoteKind.LIKE);

        assertEquals(VoteResult.REMOVED, voting.vote(voicing.id(), "masha", VoteKind.LIKE));
        assertTrue(voting.voteOf(voicing.id(), "masha").isEmpty());
    }

    @Test
    void oppositeVoteReplaces() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");
        voting.vote(voicing.id(), "masha", VoteKind.LIKE);

        assertEquals(VoteResult.CHANGED, voting.vote(voicing.id(), "masha", VoteKind.DISLIKE));
        assertEquals(VoteKind.DISLIKE, voting.voteOf(voicing.id(), "masha").orElseThrow());
        assertEquals(1, fixture.voicingRepository.votes(voicing.id()).size());
    }

    @Test
    void votingForOwnVoicingIsRejected() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");

        assertEquals(VoteResult.REJECTED_OWN, voting.vote(voicing.id(), "sergey", VoteKind.LIKE));
        assertTrue(fixture.voicingRepository.votes(voicing.id()).isEmpty());
    }

    @Test
    void votingForDraftIsRejected() {
        Voicing draft = fixture.draft(book, "Волк", "sergey");

        assertEquals(VoteResult.REJECTED_DRAFT, voting.vote(draft.id(), "masha", VoteKind.LIKE));
    }

    @Test
    void votingForArchivedIsRejectedLikeForDraft() {
        Voicing archived = fixture.withStatus(fixture.draft(book, "Волк", "sergey"), VoicingStatus.ARCHIVED);

        assertEquals(VoteResult.REJECTED_DRAFT, voting.vote(archived.id(), "masha", VoteKind.LIKE));
        assertTrue(fixture.voicingRepository.votes(archived.id()).isEmpty());
    }

    @Test
    void votingForMissingVoicingIsAnError() {
        assertThrows(EntityNotFoundException.class, () -> voting.vote(999L, "masha", VoteKind.LIKE));
    }
}
