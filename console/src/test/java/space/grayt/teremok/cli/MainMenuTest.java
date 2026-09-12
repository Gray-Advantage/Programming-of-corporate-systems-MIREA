package space.grayt.teremok.cli;

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
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.FakeAudioPlayer;
import space.grayt.teremok.audio.FakeAudioRecorder;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.FileProfileRepository;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.ProfileRepository;
import space.grayt.teremok.storage.VoicingRepository;

class MainMenuTest {

    private static final Profile SERGEY = new Profile("sergey", "Сергей");
    private static final String WARNING = "Роль битая пропущена";

    /**
     * В meta.txt нет обязательных полей, поэтому хранилище пропускает роль и заводит предупреждение.
     * Предупреждение появляется при первом заходе в «Мои озвучки» и должно быть показано ровно один
     * раз, а не на каждом витке меню.
     */
    @Test
    void предупреждениеОБитойРолиПечатаетсяОдинРаз(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("voicings").resolve("битая"));
        Files.write(dir.resolve("voicings").resolve("битая").resolve("meta.txt"),
                List.of("book=shapochka"), UTF_8);

        String printed = run(dir, "3\n0\n3\n0\n0\n");

        assertTrue(printed.contains(WARNING), () -> "предупреждения нет вовсе:\n" + printed);
        assertEquals(1, count(printed, WARNING), () -> "предупреждение повторилось:\n" + printed);
    }

    private static String run(Path dir, String input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        BookLibrary books = new BookLibrary();
        FakeAudioPlayer player = new FakeAudioPlayer();
        VoicingRepository repository = new FileVoicingRepository(dir);
        ProfileRepository profiles = new FileProfileRepository(dir);
        VotingService voting = new VotingService(repository);
        CastBuilder castBuilder = new CastBuilder(voting);
        VoicingService voicings = new VoicingService(repository, new FakeAudioRecorder(),
                Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC));
        PlaybackService playback = new PlaybackService(repository);
        PlaybackConsole playbackConsole = new PlaybackConsole(console, player, profiles, duration -> Duration.ZERO);
        RecordFlow record = new RecordFlow(console, books, voicings, player);
        ListenFlow listen = new ListenFlow(console, books, castBuilder, voting, playback, playbackConsole,
                player, profiles);
        MyVoicingsScreen mine = new MyVoicingsScreen(console, books, voicings, voting, castBuilder,
                playback, playbackConsole, record);

        assertEquals(MainMenu.MenuExit.QUIT,
                new MainMenu(console, listen, record, mine, repository).run(SERGEY));
        return out.toString(UTF_8);
    }

    private static int count(String text, String fragment) {
        int found = 0;
        for (int at = text.indexOf(fragment); at >= 0; at = text.indexOf(fragment, at + fragment.length())) {
            found++;
        }
        return found;
    }
}
