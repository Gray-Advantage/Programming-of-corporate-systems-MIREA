package space.grayt.teremok.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.Fixture;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;

class PlaybackServiceTest {

    private Fixture fixture;
    private TextWork book;
    private String wolf;
    private String hood;
    private PlaybackService playback;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        fixture = Fixture.empty(dir.resolve("audio"));
        book = fixture.addTextWork("Красная Шапочка",
                "Волк: Куда ты идёшь?",
                "Шапочка: К бабушке.",
                "Волк: А где живёт бабушка?");
        wolf = fixture.voicePartId(book, "Волк");
        hood = fixture.voicePartId(book, "Шапочка");
        playback = fixture.playback();
    }

    private Voicing wolfWithAudio(String author, int... fragments) {
        Voicing voicing = fixture.withStatus(fixture.draft(book, "Волк", author), VoicingStatus.PUBLISHED);
        return fixture.record(voicing, fragments);
    }

    @Test
    void voicedFragmentsPlayAudioOthersAreText() {
        Voicing voicing = wolfWithAudio("sergey", 1, 3);

        List<PlaybackStep> steps = playback.plan(book, Cast.empty().with(wolf, voicing.id()));

        assertEquals(3, steps.size());
        assertTrue(steps.get(0).isSpoken());
        assertEquals("sergey", steps.get(0).authorId());
        assertFalse(steps.get(1).isSpoken());
        assertNull(steps.get(1).authorId());
        assertTrue(steps.get(2).isSpoken());
    }

    @Test
    void audioComesFromStoredPathInsideAudioDirectory() {
        Voicing voicing = wolfWithAudio("sergey", 1);

        PlaybackStep first = playback.plan(book, Cast.empty().with(wolf, voicing.id())).get(0);

        assertEquals(fixture.audio.root().resolve(voicing.id() + "/fragment-0001.wav"), first.audio());
    }

    @Test
    void fragmentOrderAndVoicePartNamesArePreserved() {
        Cast cast = Cast.empty().with(wolf, wolfWithAudio("sergey", 1, 3).id());

        List<PlaybackStep> steps = playback.plan(book, cast);

        assertEquals(List.of(1, 2, 3), steps.stream().map(step -> step.fragment().number()).toList());
        assertEquals(List.of("Волк", "Шапочка", "Волк"), steps.stream().map(PlaybackStep::voicePartName).toList());
    }

    @Test
    void unrecordedFragmentIsReadAsText() {
        Voicing voicing = wolfWithAudio("sergey", 1);

        List<PlaybackStep> steps = playback.plan(book, Cast.empty().with(wolf, voicing.id()));

        assertTrue(steps.get(0).isSpoken());
        assertFalse(steps.get(2).isSpoken());
    }

    @Test
    void recordingWhoseFileIsGoneIsReadAsText() throws Exception {
        Voicing voicing = wolfWithAudio("sergey", 1, 3);
        Files.delete(fixture.audio.fileFor(voicing.id(), 3));

        assertFalse(playback.plan(book, Cast.empty().with(wolf, voicing.id())).get(2).isSpoken());
    }

    @Test
    void emptyFileCountsAsMissing() throws Exception {
        Voicing voicing = wolfWithAudio("sergey", 1, 3);
        Files.writeString(fixture.audio.fileFor(voicing.id(), 3), "");

        assertFalse(playback.plan(book, Cast.empty().with(wolf, voicing.id())).get(2).isSpoken());
    }

    @Test
    void pathLeadingOutsideAudioDirectoryIsNotPlayed() throws Exception {
        Voicing voicing = wolfWithAudio("sergey");
        Files.writeString(fixture.audio.root().resolveSibling("outside.wav"), "звук");
        fixture.voicingRepository.markRecorded(voicing.id(), 1, "../outside.wav", 1000);

        assertFalse(playback.plan(book, Cast.empty().with(wolf, voicing.id())).get(0).isSpoken());
    }

    @Test
    void referenceToMissingVoicingDoesNotBreakPlan() {
        List<PlaybackStep> steps = playback.plan(book, Cast.empty().with(hood, 999L));

        assertEquals(3, steps.size());
        assertTrue(steps.stream().noneMatch(PlaybackStep::isSpoken));
    }

    @Test
    void emptyCastGivesTextOnlyPlan() {
        List<PlaybackStep> steps = playback.plan(book, Cast.empty());

        assertEquals(3, steps.size());
        assertTrue(steps.stream().noneMatch(PlaybackStep::isSpoken));
    }

    @Test
    void sampleIsFirstRecordedFragment() {
        Voicing voicing = wolfWithAudio("sergey", 3, 1);

        assertEquals(fixture.audio.fileFor(voicing.id(), 1), playback.sample(voicing).orElseThrow());
    }

    @Test
    void voicingWithoutRecordingsHasNoSample() {
        assertTrue(playback.sample(wolfWithAudio("sergey")).isEmpty());
    }
}
