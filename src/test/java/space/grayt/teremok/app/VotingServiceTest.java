package space.grayt.teremok.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.domain.RatedVoicing;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;

class VotingServiceTest {

    private VoicingRepository repository;
    private VotingService voting;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repository = new FileVoicingRepository(dir);
        voting = new VotingService(repository);
    }

    private Voicing published(String author, String createdAt) {
        Voicing voicing = Voicing.newDraft("shapochka", "волк", author, Instant.parse(createdAt))
                .withStatus(VoicingStatus.PUBLISHED);
        repository.save(voicing);
        return voicing;
    }

    private void likes(Voicing voicing, int count) {
        for (int i = 0; i < count; i++) {
            repository.putVote(voicing.id(), "fan" + i, VoteKind.LIKE);
        }
    }

    private void dislikes(Voicing voicing, int count) {
        for (int i = 0; i < count; i++) {
            repository.putVote(voicing.id(), "hater" + i, VoteKind.DISLIKE);
        }
    }

    @Test
    void рейтингЭтоРазницаЛайковИДизлайков() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");
        likes(voicing, 5);
        dislikes(voicing, 2);

        RatedVoicing rated = voting.rate(repository.find(voicing.id()).orElseThrow());

        assertEquals(5, rated.likes());
        assertEquals(2, rated.dislikes());
        assertEquals(3, rated.score());
    }

    @Test
    void сортировкаПоРейтингуПоУбыванию() {
        Voicing слабый = published("weak", "2026-09-01T10:00:00Z");
        Voicing сильный = published("strong", "2026-09-01T10:00:00Z");
        likes(слабый, 1);
        likes(сильный, 4);

        List<RatedVoicing> ranked = voting.ranked("shapochka", "волк");

        assertEquals("strong", ranked.get(0).voicing().authorId());
        assertEquals("weak", ranked.get(1).voicing().authorId());
    }

    @Test
    void приРавномРейтингеВышеТотУКогоБольшеЛайков() {
        Voicing тихий = published("quiet", "2026-09-01T10:00:00Z");
        Voicing громкий = published("loud", "2026-09-01T10:00:00Z");
        likes(тихий, 1);
        dislikes(тихий, 0);
        likes(громкий, 5);
        dislikes(громкий, 4);

        List<RatedVoicing> ranked = voting.ranked("shapochka", "волк");

        assertEquals("loud", ranked.get(0).voicing().authorId());
    }

    @Test
    void приПолномРавенствеВышеБолееСвежая() {
        published("old", "2026-09-01T10:00:00Z");
        published("new", "2026-09-05T10:00:00Z");

        List<RatedVoicing> ranked = voting.ranked("shapochka", "волк");

        assertEquals("new", ranked.get(0).voicing().authorId());
    }

    @Test
    void черновикиНеПопадаютВСписок() {
        repository.save(Voicing.newDraft("shapochka", "волк", "draft", Instant.parse("2026-09-01T10:00:00Z")));
        published("ready", "2026-09-01T10:00:00Z");

        List<RatedVoicing> ranked = voting.ranked("shapochka", "волк");

        assertEquals(1, ranked.size());
        assertEquals("ready", ranked.get(0).voicing().authorId());
    }

    @Test
    void первыйГолосДобавляется() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");

        assertEquals(VoteResult.ADDED, voting.vote(voicing.id(), "masha", VoteKind.LIKE));
        assertEquals(VoteKind.LIKE, voting.voteOf(voicing.id(), "masha").orElseThrow());
    }

    @Test
    void повторныйТакойЖеГолосСнимается() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");
        voting.vote(voicing.id(), "masha", VoteKind.LIKE);

        assertEquals(VoteResult.REMOVED, voting.vote(voicing.id(), "masha", VoteKind.LIKE));
        assertTrue(voting.voteOf(voicing.id(), "masha").isEmpty());
    }

    @Test
    void противоположныйГолосЗаменяет() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");
        voting.vote(voicing.id(), "masha", VoteKind.LIKE);

        assertEquals(VoteResult.CHANGED, voting.vote(voicing.id(), "masha", VoteKind.DISLIKE));
        assertEquals(VoteKind.DISLIKE, voting.voteOf(voicing.id(), "masha").orElseThrow());
    }

    @Test
    void заСвоюРольГолосоватьНельзя() {
        Voicing voicing = published("sergey", "2026-09-01T10:00:00Z");

        assertEquals(VoteResult.REJECTED_OWN, voting.vote(voicing.id(), "sergey", VoteKind.LIKE));
        assertTrue(repository.votes(voicing.id()).isEmpty());
    }

    @Test
    void заЧерновикГолосоватьНельзя() {
        Voicing draft = Voicing.newDraft("shapochka", "волк", "sergey", Instant.parse("2026-09-01T10:00:00Z"));
        repository.save(draft);

        assertEquals(VoteResult.REJECTED_DRAFT, voting.vote(draft.id(), "masha", VoteKind.LIKE));
    }

    @Test
    void голосованиеЗаНесуществующуюРольОшибка() {
        assertThrows(IllegalArgumentException.class,
                () -> voting.vote("нет__такой__роли", "masha", VoteKind.LIKE));
    }
}
