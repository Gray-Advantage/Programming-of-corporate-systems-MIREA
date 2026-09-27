package space.grayt.teremok;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.audio.FakeAudioPlayer;
import space.grayt.teremok.audio.FakeAudioRecorder;
import space.grayt.teremok.cli.Console;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;

class ScenarioTest {

    private static final String MAMA = Voicing.idOf("shapochka", "мама", "sergey");

    /**
     * Full path: Sergey records and publishes a voicing, Masha listens to it and likes it.
     * Each input line answers one screen prompt.
     */
    // Profile names are Latin: Profile.of() derives the id from the name via
    // toLowerCase(), and MAMA and the expected Vote below rely on the Latin ids sergey and masha.
    // A Cyrillic name would give a Cyrillic id and a voicing under another key; this test caught that.
    private static final String INPUT = String.join("\n",
            "n", "sergey",  // create a profile and sign in
            "2",             // Record a book
            "1",             // Little Red Riding Hood
            "2",             // speaker Mother, who has one line
            "",              // Enter starts recording
            "",              // Enter stops
            "3",             // next: no unrecorded lines are left
            "0",             // close the line list
            "0",             // back from speaker selection
            "3",             // My voicings
            "1",             // open the voicing
            "3",             // publish
            "0",             // back to the voicing list
            "0",             // back to the menu
            "4",             // switch profile
            "n", "masha",   // create a second profile
            "1",             // Listen to a book
            "1",             // Little Red Riding Hood
            "n",             // change a voice
            "2",             // speaker Mother
            "l 1",           // like the only voicing
            "0",             // back to the cast
            "s") + "\n";    // listen; then end of input closes all menus

    @Test
    void endToEndRecordPublishAndLike(@TempDir Path dir) throws Exception {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        FakeAudioPlayer player = new FakeAudioPlayer();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(INPUT.getBytes(UTF_8)), out);

        // Zero reading pause: the scenario reaches PlaybackConsole.play and must not actually
        // wait through the pauses of unvoiced lines.
        new App(dir, console, recorder, player,
                Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC),
                duration -> Duration.ZERO).run();

        VoicingRepository repository = new FileVoicingRepository(dir);
        Voicing mama = repository.find(MAMA).orElseThrow(
                () -> new AssertionError("Роль не найдена: " + MAMA + "\n--- вывод сценария ---\n"
                        + out.toString(UTF_8)));
        assertEquals(VoicingStatus.PUBLISHED, mama.status());
        assertEquals(List.of(new Vote("masha", VoteKind.LIKE)), repository.votes(MAMA));
        assertTrue(Files.size(repository.audioFile(MAMA, 2)) > 0);
        assertEquals(1, player.played().size());
        assertTrue(out.toString(UTF_8).contains("До встречи"));
    }

    @Test
    void quittingRightAfterStartPreparesDataDirectory(@TempDir Path dir) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream("0\n".getBytes(UTF_8)), out);

        new App(dir.resolve("data"), console, new FakeAudioRecorder(), new FakeAudioPlayer(),
                Clock.systemUTC()).run();

        assertTrue(Files.isDirectory(dir.resolve("data")));
        assertTrue(out.toString(UTF_8).contains("До встречи"));
    }
}
