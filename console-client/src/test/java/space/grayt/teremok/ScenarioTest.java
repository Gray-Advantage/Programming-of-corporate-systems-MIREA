package space.grayt.teremok;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.cli.Console;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;

class ScenarioTest {

    /**
     * Full path: Sergey records and publishes a voicing, Masha listens to it and likes it.
     * Each input line answers one screen prompt.
     */
    // Profile names are Latin so that the ids are sergey and masha, which the checks below use.
    private static final String INPUT = String.join("\n",
            "n", "sergey",   // create a profile and sign in
            "2",             // Record a text work
            "2",             // Little Red Riding Hood (text works are listed by title)
            "2",             // voice part Mother, who has one fragment
            "",              // Enter starts recording
            "",              // Enter stops
            "3",             // next: no unrecorded fragments are left
            "0",             // close the fragment list
            "0",             // back from voice part selection
            "3",             // My voicings
            "1",             // open the voicing
            "3",             // publish
            "0",             // back to the voicing list
            "0",             // back to the menu
            "4",             // switch profile
            "n", "masha",    // create a second profile
            "1",             // Listen to a text work
            "2",             // Little Red Riding Hood
            "n",             // change a voice
            "2",             // voice part Mother
            "l 1",           // like the only voicing
            "0",             // back to the cast
            "s") + "\n";     // listen; then end of input closes all menus

    @Test
    void endToEndRecordPublishAndLike(@TempDir Path dir) throws Exception {
        Fixture fixture = Fixture.withSeedTextWorks(dir);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(INPUT.getBytes(UTF_8)), out);

        // Zero reading pause: the scenario reaches PlaybackConsole.play and must not actually
        // wait through the pauses of unvoiced fragments.
        new App(fixture.database, fixture.textWorks, fixture.audio, console, fixture.recorder, fixture.player,
                fixture.clock, duration -> Duration.ZERO).run();

        TextWork shapochka = fixture.textWork("Красная Шапочка");
        Voicing mama = fixture.voicingRepository
                .findByVoicePartAndAuthor(fixture.voicePartId(shapochka, "Мама"), "sergey")
                .orElseThrow(() -> new AssertionError("Роль не найдена\n--- вывод сценария ---\n"
                        + out.toString(UTF_8)));
        assertEquals(VoicingStatus.PUBLISHED, mama.status());
        assertEquals(List.of(new Vote("masha", VoteKind.LIKE)), fixture.voicingRepository.votes(mama.id()));
        assertTrue(Files.size(fixture.audio.fileFor(mama.id(), 2)) > 0);
        assertEquals(List.of(fixture.audio.fileFor(mama.id(), 2)), fixture.player.played());
        assertTrue(out.toString(UTF_8).contains("До встречи"));
    }
}
