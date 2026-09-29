package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.Fixture;
import space.grayt.teremok.app.ProfileService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.db.TestDatabase;
import space.grayt.teremok.domain.Profile;

class MainMenuTest {

    private Fixture fixture;
    private Profile sergey;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        fixture = Fixture.withSeedTextWorks(dir);
        sergey = fixture.profile("Sergey");
    }

    private MainMenu.MenuExit run(String input, ByteArrayOutputStream out) {
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        ProfileService profiles = fixture.profiles();
        VotingService voting = fixture.voting();
        VoicingService voicings = fixture.voicings();
        PlaybackConsole playbackConsole = new PlaybackConsole(console, fixture.player, profiles, duration -> Duration.ZERO);
        RecordFlow record = new RecordFlow(console, fixture.textWorks, voicings, fixture.player);
        ListenFlow listen = new ListenFlow(console, fixture.textWorks, fixture.castBuilder(), voting,
                fixture.playback(), playbackConsole, fixture.player, profiles);
        MyVoicingsScreen mine = new MyVoicingsScreen(console, fixture.textWorks, voicings, voting,
                fixture.castBuilder(), fixture.playback(), playbackConsole, record);
        return new MainMenu(console, listen, record, mine).run(sergey);
    }

    /** A lost table stands in for a database that failed mid-session. */
    @Test
    void databaseFailureIsExplainedAndMenuStays() {
        TestDatabase.execute(fixture.database, "DROP TABLE voicings CASCADE");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        MainMenu.MenuExit exit = run("3\n3\n0\n", out);

        String printed = out.toString(UTF_8);
        assertEquals(MainMenu.MenuExit.QUIT, exit);
        assertEquals(2, count(printed, "Не удалось прочитать озвучки автора"), printed);
        assertEquals(3, count(printed, "Теремок — Sergey"), printed);
    }

    @Test
    void switchingProfileLeavesMenu() {
        assertEquals(MainMenu.MenuExit.SWITCH_PROFILE, run("4\n", new ByteArrayOutputStream()));
    }

    @Test
    void unknownCommandIsExplained() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        run("9\n0\n", out);

        assertTrue(out.toString(UTF_8).contains("Не понимаю. Введите число от 0 до 6."));
    }

    private static int count(String text, String fragment) {
        int found = 0;
        for (int at = text.indexOf(fragment); at >= 0; at = text.indexOf(fragment, at + fragment.length())) {
            found++;
        }
        return found;
    }
}
