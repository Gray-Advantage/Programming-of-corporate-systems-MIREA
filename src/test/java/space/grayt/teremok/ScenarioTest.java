package space.grayt.teremok;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
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
     * Полный путь: Сергей записывает и публикует роль, Маша слушает её и ставит лайк.
     * Каждая строка ввода — один ответ на приглашение экрана.
     */
    // Имена профилей — латиницей: Profile.of() приводит имя к идентификатору через
    // toLowerCase(), а MAMA и ожидаемый Vote ниже завязаны на латинские id "sergey"/"masha".
    // Кириллическое "Сергей" дало бы id "сергей" и роль под другим ключом — тест это поймал.
    private static final String INPUT = String.join("\n",
            "n", "sergey",  // создать профиль и войти
            "2",             // Озвучить книгу
            "1",             // Красная Шапочка
            "2",             // персонаж Мама, у него одна реплика
            "",              // Enter — начать запись
            "",              // Enter — стоп
            "3",             // дальше: незаписанных реплик не осталось
            "0",             // закрыть список реплик
            "0",             // назад из выбора персонажа
            "3",             // Мои озвучки
            "1",             // открыть роль
            "3",             // опубликовать
            "0",             // назад к списку ролей
            "0",             // назад в меню
            "4",             // сменить профиль
            "n", "masha",   // создать второй профиль
            "1",             // Слушать книгу
            "1",             // Красная Шапочка
            "n",             // сменить голос
            "2",             // персонаж Мама
            "l 1",           // лайк единственной озвучке
            "0",             // назад к касту
            "s") + "\n";    // слушать; дальше конец ввода закрывает все меню

    @Test
    void сквознойСценарийЗаписиПубликацииИЛайка(@TempDir Path dir) throws Exception {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        FakeAudioPlayer player = new FakeAudioPlayer();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(INPUT.getBytes(UTF_8)), out);

        new App(dir, console, recorder, player,
                Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC)).run();

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
    void выходСразуПослеСтартаГотовитКаталогДанных(@TempDir Path dir) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream("0\n".getBytes(UTF_8)), out);

        new App(dir.resolve("data"), console, new FakeAudioRecorder(), new FakeAudioPlayer(),
                Clock.systemUTC()).run();

        assertTrue(Files.isDirectory(dir.resolve("data")));
        assertTrue(out.toString(UTF_8).contains("До встречи"));
    }
}
