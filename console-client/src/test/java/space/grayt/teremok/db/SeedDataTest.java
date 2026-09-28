package space.grayt.teremok.db;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.RatedVoicing;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.storage.JdbcProfileRepository;
import space.grayt.teremok.storage.JdbcVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;
import space.grayt.teremok.textwork.JdbcTextWorkCatalog;

/** seed.sql loads into H2 as into PostgreSQL, and the repositories read it the way the demo expects. */
class SeedDataTest {

    private VoicingRepository voicings;
    private JdbcTextWorkCatalog textWorks;
    private TextWork kolobok;

    @BeforeEach
    void setUp() {
        DatabaseManager database = TestDatabase.withSeed();
        voicings = new JdbcVoicingRepository(database);
        textWorks = new JdbcTextWorkCatalog(database);
        kolobok = TestDatabase.textWork(textWorks, "Колобок");
    }

    @Test
    void grayHasEightPublishedKolobokVoicings() {
        List<Voicing> gray = voicings.findByAuthor("gray");

        assertEquals(8, gray.size());
        assertTrue(gray.stream().allMatch(voicing -> voicing.status() == VoicingStatus.PUBLISHED));
        assertTrue(gray.stream().allMatch(voicing -> voicing.textWorkId().equals(kolobok.id())));
        assertTrue(gray.stream().allMatch(voicing -> voicing.recordedFragments().size()
                == kolobok.fragmentsOf(voicing.voicePartId()).size()));
    }

    @Test
    void seedHasEveryStatus() {
        Map<VoicingStatus, Long> byStatus = textWorks.all().stream()
                .flatMap(textWork -> voicings.findByTextWork(textWork.id()).stream())
                .collect(Collectors.groupingBy(Voicing::status, Collectors.counting()));

        assertEquals(Map.of(VoicingStatus.PUBLISHED, 11L, VoicingStatus.DRAFT, 3L, VoicingStatus.ARCHIVED, 2L),
                byStatus);
    }

    @Test
    void everyKolobokRoleIsCastAndDanyasFoxWins() {
        VotingService voting = new VotingService(voicings);
        Cast cast = new CastBuilder(voting).best(kolobok);
        String fox = TestDatabase.voicePartId(kolobok, "Лиса");

        assertEquals(kolobok.voiceParts().size(), cast.voicingByVoicePart().size());
        List<RatedVoicing> foxes = voting.ranked(kolobok.id(), fox);
        assertEquals(List.of("danya", "gray"), foxes.stream().map(rated -> rated.voicing().authorId()).toList());
        assertEquals(List.of(2, 1), foxes.stream().map(RatedVoicing::score).toList());
        assertEquals(foxes.get(0).voicing().id(), cast.voicingFor(fox).orElseThrow());
    }

    @Test
    void seedProfilesAreListedInOrderOfCreation() {
        assertEquals(List.of("gray", "sasha", "semyon", "danya", "masha", "petya"),
                new JdbcProfileRepository(TestDatabase.withSeed()).findAll().stream()
                        .map(Profile::id).toList());
    }

    @Test
    void newVoicingIdsContinueAfterTheSeed() {
        Voicing inserted = voicings.insert(TestDatabase.voicePartId(kolobok, "Заяц"), "sasha", Instant.now());

        assertEquals(100, inserted.id());
    }
}
