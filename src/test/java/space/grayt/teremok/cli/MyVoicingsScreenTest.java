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
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.FakeAudioPlayer;
import space.grayt.teremok.audio.FakeAudioRecorder;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;

class MyVoicingsScreenTest {

    private static final Profile SERGEY = new Profile("sergey", "Сергей");
    private static final BookLibrary BOOKS = new BookLibrary();
    private static final String MAMA_ID = Voicing.idOf("shapochka", "мама", "sergey");

    private VoicingRepository repository;
    private FakeAudioPlayer player;
    private FakeAudioRecorder recorder;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repository = new FileVoicingRepository(dir);
        player = new FakeAudioPlayer();
        recorder = new FakeAudioRecorder();
    }

    /** Роль «Мама» с единственной репликой 2, полностью записанная. */
    private Voicing completeMama() throws Exception {
        Voicing voicing = Voicing.newDraft("shapochka", "мама", "sergey", Instant.parse("2026-09-01T10:00:00Z"));
        repository.save(voicing);
        Path file = repository.audioFile(voicing.id(), 2);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "звук");
        return voicing;
    }

    private void run(String input) {
        out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        VoicingService voicings = new VoicingService(repository, recorder,
                Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC));
        VotingService voting = new VotingService(repository);
        PlaybackService playback = new PlaybackService(repository);
        PlaybackConsole playbackConsole = new PlaybackConsole(console, player);
        RecordFlow record = new RecordFlow(console, BOOKS, voicings, player);
        new MyVoicingsScreen(console, BOOKS, voicings, voting, new CastBuilder(voting), playback,
                playbackConsole, record).run(SERGEY);
    }

    private String printed() {
        return out.toString(UTF_8);
    }

    @Test
    void пустойСписокСообщаетЧтоОзвучекНет() {
        run("0\n");

        assertTrue(printed().contains("Вы пока ничего не озвучивали"));
    }

    @Test
    void показываетКнигуПерсонажаСтатусИПрогресс() throws Exception {
        completeMama();

        run("0\n");

        assertTrue(printed().contains("Красная Шапочка"));
        assertTrue(printed().contains("Мама"));
        assertTrue(printed().contains("черновик"));
        assertTrue(printed().contains("1/1"));
    }

    @Test
    void публикуетПолнуюРоль() throws Exception {
        completeMama();

        run("1\n3\n0\n0\n");

        assertEquals(VoicingStatus.PUBLISHED, repository.find(MAMA_ID).orElseThrow().status());
    }

    @Test
    void неполнуюРольПубликоватьОтказывается() throws Exception {
        Voicing voicing = Voicing.newDraft("shapochka", "шапочка", "sergey", Instant.parse("2026-09-01T10:00:00Z"));
        repository.save(voicing);

        run("1\n3\n0\n0\n");

        assertEquals(VoicingStatus.DRAFT, repository.find(voicing.id()).orElseThrow().status());
        assertTrue(printed().contains("полностью записанную"));
    }

    @Test
    void снимаетРольСПубликации() throws Exception {
        repository.save(completeMama().withStatus(VoicingStatus.PUBLISHED));

        run("1\n3\n0\n0\n");

        assertEquals(VoicingStatus.DRAFT, repository.find(MAMA_ID).orElseThrow().status());
    }

    @Test
    void прослушиваетРольЦеликомДажеЧерновиком() throws Exception {
        completeMama();

        run("1\n2\n");

        assertEquals(1, player.played().size());
    }

    @Test
    void удаляетРольПослеПодтверждения() throws Exception {
        completeMama();

        run("1\n4\nда\n0\n");

        assertTrue(repository.find(MAMA_ID).isEmpty());
    }

    @Test
    void безПодтвержденияРольОстаётся() throws Exception {
        completeMama();

        run("1\n4\nнет\n0\n0\n");

        assertTrue(repository.find(MAMA_ID).isPresent());
    }
}
