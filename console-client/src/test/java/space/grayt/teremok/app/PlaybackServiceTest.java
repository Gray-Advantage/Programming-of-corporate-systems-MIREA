package space.grayt.teremok.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.book.BookParser;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.Speaker;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;

class PlaybackServiceTest {

    private static final Book BOOK = BookParser.parse("shapochka", """
            title: Красная Шапочка
            ---
            Волк: Куда ты идёшь?
            Шапочка: К бабушке.
            Волк: А где живёт бабушка?
            """);
    private static final String WOLF = Speaker.idOf("Волк");
    private static final String HOOD = Speaker.idOf("Шапочка");

    private VoicingRepository repository;
    private PlaybackService playback;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repository = new FileVoicingRepository(dir);
        playback = new PlaybackService(repository);
    }

    private Voicing withAudio(String speakerId, String author, int... lines) throws Exception {
        Voicing voicing = Voicing.newDraft("shapochka", speakerId, author, Instant.parse("2026-09-01T10:00:00Z"))
                .withStatus(VoicingStatus.PUBLISHED);
        repository.save(voicing);
        for (int line : lines) {
            Path file = repository.audioFile(voicing.id(), line);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "звук");
        }
        return voicing;
    }

    @Test
    void voicedLinesPlayAudioOthersAreText() throws Exception {
        Voicing wolf = withAudio(WOLF, "sergey", 1, 3);
        Cast cast = Cast.empty().with(WOLF, wolf.id());

        List<PlaybackStep> steps = playback.plan(BOOK, cast);

        assertEquals(3, steps.size());
        assertTrue(steps.get(0).isSpoken());
        assertEquals("sergey", steps.get(0).authorId());
        assertFalse(steps.get(1).isSpoken());
        assertNull(steps.get(1).authorId());
        assertTrue(steps.get(2).isSpoken());
    }

    @Test
    void lineOrderAndSpeakerNamesArePreserved() throws Exception {
        Cast cast = Cast.empty().with(WOLF, withAudio(WOLF, "sergey", 1, 3).id());

        List<PlaybackStep> steps = playback.plan(BOOK, cast);

        assertEquals(List.of(1, 2, 3), steps.stream().map(step -> step.line().number()).toList());
        assertEquals(List.of("Волк", "Шапочка", "Волк"),
                steps.stream().map(PlaybackStep::speakerName).toList());
    }

    @Test
    void missingFileIsReadAsText() throws Exception {
        Voicing wolf = withAudio(WOLF, "sergey", 1);
        Cast cast = Cast.empty().with(WOLF, wolf.id());

        List<PlaybackStep> steps = playback.plan(BOOK, cast);

        assertTrue(steps.get(0).isSpoken());
        assertFalse(steps.get(2).isSpoken());
    }

    @Test
    void emptyFileCountsAsMissing() throws Exception {
        Voicing wolf = withAudio(WOLF, "sergey", 1);
        Files.writeString(repository.audioFile(wolf.id(), 3), "");
        Cast cast = Cast.empty().with(WOLF, wolf.id());

        assertFalse(playback.plan(BOOK, cast).get(2).isSpoken());
    }

    @Test
    void referenceToMissingVoicingDoesNotBreakPlan() {
        Cast cast = Cast.empty().with(HOOD, "нет__такой__роли");

        List<PlaybackStep> steps = playback.plan(BOOK, cast);

        assertEquals(3, steps.size());
        assertTrue(steps.stream().noneMatch(PlaybackStep::isSpoken));
    }

    @Test
    void emptyCastGivesTextOnlyPlan() {
        List<PlaybackStep> steps = playback.plan(BOOK, Cast.empty());

        assertEquals(3, steps.size());
        assertTrue(steps.stream().noneMatch(PlaybackStep::isSpoken));
    }
}
