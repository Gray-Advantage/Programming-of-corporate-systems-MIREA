package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.Fixture;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;

/**
 * Text work 2 is Красная Шапочка (the list is ordered by title), voice part 2 is Мама with a single
 * fragment, voice part 1 is Рассказчик.
 */
class RecordFlowTest {

    private Fixture fixture;
    private TextWork shapochka;
    private Profile sergey;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        fixture = Fixture.withSeedTextWorks(dir);
        shapochka = fixture.textWork("Красная Шапочка");
        sergey = fixture.profile("Sergey");
    }

    private void run(String input) {
        out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        new RecordFlow(console, fixture.textWorks, fixture.voicings(), fixture.player).run(sergey);
    }

    private String printed() {
        return out.toString(UTF_8);
    }

    private Voicing voicingOf(String voicePartName) {
        return fixture.voicingRepository
                .findByVoicePartAndAuthor(fixture.voicePartId(shapochka, voicePartName), "sergey")
                .orElseThrow(() -> new AssertionError("Нет озвучки " + voicePartName + "\n" + printed()));
    }

    @Test
    void recordsFragmentAndSavesDraft() {
        run("2\n2\n\n\n3\n0\n0\n");

        Voicing voicing = voicingOf("Мама");
        assertEquals(1, voicing.recordedFragments().size());
        assertEquals(1, fixture.recorder.recorded().size());
        assertEquals(Map.of(2, voicing.id() + "/fragment-0002.wav"),
                fixture.voicingRepository.audioPaths(voicing.id()));
    }

    @Test
    void progressIsShownInVoicePartList() {
        run("2\n2\n\n\n3\n0\n1\n0\n0\n");

        assertTrue(printed().contains("1/1"));
    }

    @Test
    void browsingVoicePartsCreatesNoDrafts() {
        run("2\n0\n");

        assertTrue(fixture.voicingRepository.findByAuthor("sergey").isEmpty());
    }

    @Test
    void listeningAfterRecordingUsesPlayer() {
        run("2\n2\n\n\n1\n3\n0\n0\n");

        assertEquals(1, fixture.player.played().size());
    }

    @Test
    void rerecordingReplacesSameFragmentFile() {
        run("2\n2\n\n\n2\n\n3\n0\n0\n");

        assertEquals(2, fixture.recorder.recorded().size());
        assertEquals(fixture.recorder.recorded().get(0), fixture.recorder.recorded().get(1));
        assertEquals(1, fixture.voicingRepository.audioPaths(voicingOf("Мама").id()).size());
    }

    @Test
    void recordedFragmentCanBeRerecordedFromFragmentList() {
        run("2\n2\n\n\n3\n1\n\n\n3\n0\n0\n");

        assertEquals(2, fixture.recorder.recorded().size());
        assertTrue(printed().contains("[готово]"));
    }

    @Test
    void quittingMidwayKeepsRecordedFragments() throws Exception {
        run("2\n1\n\n\n3\n0\n0\n");

        Voicing narrator = voicingOf("Рассказчик");
        assertEquals(1, narrator.recordedFragments().size());
        assertTrue(Files.size(fixture.audio.fileFor(narrator.id(), 1)) > 0);
    }

    @Test
    void unavailableMicrophoneDoesNotBreakFlow() {
        fixture.recorder.setAvailable(false);

        run("2\n2\n\n0\n0\n");

        assertTrue(printed().contains("Микрофон недоступен"));
        assertTrue(voicingOf("Мама").recordedFragments().isEmpty());
    }

    @Test
    void reportsWhenAllFragmentsAreRecorded() {
        run("2\n2\n\n\n3\n0\n2\n0\n0\n");

        assertTrue(printed().contains("Все фрагменты записаны"));
    }

    @Test
    void fragmentCountUsesRussianPlurals() {
        run("2\n0\n0\n");

        String printed = printed();
        assertTrue(printed.contains("Красная Шапочка — 21 фрагмент"), () -> printed);
        assertTrue(printed.contains("Колобок — 18 фрагментов"), () -> printed);
        assertTrue(printed.contains("Мама — 1 фрагмент,"), () -> printed);
        assertTrue(printed.contains("Бабушка — 2 фрагмента,"), () -> printed);
    }
}
