package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.audio.FakeAudioPlayer;
import space.grayt.teremok.audio.FakeAudioRecorder;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;

class RecordFlowTest {

    private static final Profile SERGEY = new Profile("sergey", "Сергей");

    private VoicingRepository repository;
    private FakeAudioRecorder recorder;
    private FakeAudioPlayer player;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repository = new FileVoicingRepository(dir);
        recorder = new FakeAudioRecorder();
        player = new FakeAudioPlayer();
    }

    private void run(String input) {
        out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        VoicingService voicings = new VoicingService(repository, recorder,
                Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC));
        new RecordFlow(console, new BookLibrary(), voicings, player).run(SERGEY);
    }

    private String printed() {
        return out.toString(UTF_8);
    }

    /** Book 1 is Little Red Riding Hood, speaker 2 is Mother with a single line. */
    @Test
    void recordsLineAndSavesDraft() {
        run("1\n2\n\n\n3\n0\n0\n");

        Voicing voicing = repository.find(Voicing.idOf("shapochka", "мама", "sergey")).orElseThrow();
        assertEquals(1, voicing.recordedLines().size());
        assertEquals(1, recorder.recorded().size());
    }

    @Test
    void progressIsShownInSpeakerList() {
        run("1\n2\n\n\n3\n0\n1\n0\n0\n");

        assertTrue(printed().contains("1/1"));
    }

    @Test
    void browsingSpeakersCreatesNoDrafts() {
        run("1\n0\n");

        assertTrue(repository.findByAuthor("sergey").isEmpty());
    }

    @Test
    void listeningAfterRecordingUsesPlayer() {
        run("1\n2\n\n\n1\n3\n0\n0\n");

        assertEquals(1, player.played().size());
    }

    @Test
    void rerecordingReplacesSameLineFile() {
        run("1\n2\n\n\n2\n\n3\n0\n0\n");

        assertEquals(2, recorder.recorded().size());
        assertEquals(recorder.recorded().get(0), recorder.recorded().get(1));
    }

    @Test
    void recordedLineCanBeRerecordedFromLineList() {
        run("1\n2\n\n\n3\n1\n\n\n3\n0\n0\n");

        assertEquals(2, recorder.recorded().size());
        assertTrue(printed().contains("[готово]"));
    }

    @Test
    void quittingMidwayKeepsRecordedLinesOnDisk() throws Exception {
        run("1\n1\n\n\n3\n0\n0\n");

        Path file = repository.audioFile(Voicing.idOf("shapochka", "рассказчик", "sergey"), 1);
        assertTrue(Files.size(file) > 0);
    }

    @Test
    void unavailableMicrophoneDoesNotBreakFlow() {
        recorder.setAvailable(false);

        run("1\n2\n\n0\n0\n");

        assertTrue(printed().contains("Микрофон недоступен"));
    }

    @Test
    void reportsWhenAllLinesAreRecorded() {
        run("1\n2\n\n\n3\n0\n2\n0\n0\n");

        assertTrue(printed().contains("Все реплики записаны"));
    }

    @Test
    void lineCountUsesRussianPlurals() {
        run("1\n0\n0\n");

        String printed = printed();
        assertTrue(printed.contains("Красная Шапочка — 21 реплика"), () -> printed);
        assertTrue(printed.contains("Колобок — 18 реплик"), () -> printed);
        assertTrue(printed.contains("Мама — 1 реплика,"), () -> printed);
        assertTrue(printed.contains("Бабушка — 2 реплики,"), () -> printed);
    }
}
