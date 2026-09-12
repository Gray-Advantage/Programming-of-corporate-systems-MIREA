package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.FakeAudioPlayer;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;

class ListenFlowTest {

    private static final Profile MASHA = new Profile("masha", "Маша");
    private static final BookLibrary BOOKS = new BookLibrary();

    private VoicingRepository repository;
    private FakeAudioPlayer player;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repository = new FileVoicingRepository(dir);
        player = new FakeAudioPlayer();
    }

    /** Публикует роль «Мама» из «Красной Шапочки» с озвученной репликой 2. */
    private Voicing publishMama(String author, int likes) throws Exception {
        Voicing voicing = Voicing.newDraft("shapochka", "мама", author, Instant.parse("2026-09-01T10:00:00Z"))
                .withStatus(VoicingStatus.PUBLISHED);
        repository.save(voicing);
        Path file = repository.audioFile(voicing.id(), 2);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "звук");
        for (int i = 0; i < likes; i++) {
            repository.putVote(voicing.id(), "fan" + i, VoteKind.LIKE);
        }
        return voicing;
    }

    private void run(String input) {
        out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        VotingService voting = new VotingService(repository);
        PlaybackService playback = new PlaybackService(repository);
        new ListenFlow(console, BOOKS, new CastBuilder(voting), voting, playback,
                // Нулевая пауза чтения: тест не должен реально ждать неозвученные реплики.
                new PlaybackConsole(console, player, duration -> Duration.ZERO), player).run(MASHA);
    }

    private String printed() {
        return out.toString(UTF_8);
    }

    @Test
    void паузаЧтенияЗависитОтДлиныНоОграничена() {
        assertEquals(Duration.ofMillis(1200), PlaybackService.readingPause("Да."));
        assertEquals(Duration.ofMillis(8000), PlaybackService.readingPause("а".repeat(500)));
    }

    @Test
    void кастЗаполняетсяЛучшейРольюАвтоматически() throws Exception {
        publishMama("sergey", 1);

        run("1\n0\n");

        assertTrue(printed().contains("sergey"));
    }

    @Test
    void персонажБезОзвучкиПоказанКакТекстом() {
        run("1\n0\n");

        assertTrue(printed().contains("текстом"));
    }

    @Test
    void воспроизведениеИграетОзвученныеРеплики() throws Exception {
        publishMama("sergey", 1);

        run("1\ns\n");

        assertEquals(1, player.played().size());
        assertTrue(printed().contains("Книга закончилась"));
    }

    @Test
    void лайкУчитываетсяВРейтинге() throws Exception {
        Voicing mama = publishMama("sergey", 0);

        run("1\nn\n2\nl 1\n0\n0\n");

        assertEquals(1, repository.votes(mama.id()).size());
        assertTrue(printed().contains("Лайк"));
    }

    @Test
    void повторныйЛайкСнимаетГолос() throws Exception {
        Voicing mama = publishMama("sergey", 0);

        run("1\nn\n2\nl 1\nl 1\n0\n0\n");

        assertTrue(repository.votes(mama.id()).isEmpty());
    }

    @Test
    void заСвоюРольГолосоватьНельзяИЭтоОбъясняется() throws Exception {
        publishMama("masha", 0);

        run("1\nn\n2\nl 1\n0\n0\n");

        assertTrue(printed().contains("свою"));
    }

    @Test
    void голосМожноСменитьНаЧтениеТекстом() throws Exception {
        publishMama("sergey", 1);

        run("1\nn\n2\nt\ns\n");

        assertTrue(player.played().isEmpty());
    }

    @Test
    void примерРолиПроигрываетПервуюЗаписаннуюРеплику() throws Exception {
        publishMama("sergey", 1);

        run("1\nn\n2\np 1\n0\n0\n");

        assertEquals(1, player.played().size());
    }

    @Test
    void вводВоВремяВоспроизведенияОстанавливаетЕго() throws Exception {
        publishMama("sergey", 1);

        run("1\ns\nстоп\n0\n");

        assertTrue(printed().contains("Остановлено"));
    }

    @Test
    void непонятнаяКомандаНеЛомаетЭкран() {
        run("1\nчто-то\n0\n");

        assertTrue(printed().contains("Не понимаю"));
    }
}
