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
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.ProfileService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;

/** Text works are listed by title: 1 Колобок, 2 Красная Шапочка, 3 Теремок. */
class ListenFlowTest {

    private Fixture fixture;
    private TextWork shapochka;
    private Profile masha;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        fixture = Fixture.withSeedTextWorks(dir);
        shapochka = fixture.textWork("Красная Шапочка");
        masha = fixture.profile("masha");
    }

    /** Publishes the Mother voicing of Little Red Riding Hood: its only fragment 2 is recorded. */
    private Voicing publishMama(String author, int likes) {
        Voicing voicing = fixture.voicing(shapochka, "Мама", author, VoicingStatus.PUBLISHED);
        fixture.likes(voicing, likes);
        return voicing;
    }

    private void run(String input) {
        out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        VotingService voting = fixture.voting();
        ProfileService profiles = fixture.profiles();
        new ListenFlow(console, fixture.textWorks, fixture.castBuilder(), voting, fixture.playback(),
                // Zero reading pause: the test must not actually wait for unvoiced fragments.
                new PlaybackConsole(console, fixture.player, profiles, duration -> Duration.ZERO),
                fixture.player, profiles)
                .run(masha);
    }

    private String printed() {
        return out.toString(UTF_8);
    }

    @Test
    void readingPauseDependsOnLengthButIsCapped() {
        assertEquals(Duration.ofMillis(1200), PlaybackService.readingPause("Да."));
        assertEquals(Duration.ofMillis(8000), PlaybackService.readingPause("а".repeat(500)));
    }

    @Test
    void textWorksAreListedByTitle() {
        run("0\n");

        String printed = printed();
        assertTrue(printed.indexOf("1  Колобок") < printed.indexOf("2  Красная Шапочка"), printed);
        assertTrue(printed.indexOf("2  Красная Шапочка") < printed.indexOf("3  Теремок"), printed);
    }

    @Test
    void castIsFilledWithBestVoicingAutomatically() {
        publishMama("sergey", 1);

        run("2\n0\n");

        assertTrue(printed().contains("Мама  sergey  [+1]"), this::printed);
    }

    @Test
    void voicePartWithoutVoicingIsShownAsText() {
        run("2\n0\n");

        assertTrue(printed().contains("текстом"));
    }

    @Test
    void archivedVoicingIsNotOfferedForListening() {
        Voicing mama = publishMama("sergey", 3);
        fixture.withStatus(mama, VoicingStatus.ARCHIVED);

        run("2\nn\n2\n0\n0\n");

        assertTrue(printed().contains("Мама  — текстом —"), this::printed);
        assertTrue(printed().contains("Опубликованных озвучек пока нет"), this::printed);
    }

    @Test
    void playbackPlaysVoicedFragments() {
        publishMama("sergey", 1);

        run("2\ns\n");

        assertEquals(1, fixture.player.played().size());
        assertTrue(printed().contains("Произведение закончилось"));
    }

    @Test
    void likeCountsTowardScore() {
        Voicing mama = publishMama("sergey", 0);

        run("2\nn\n2\nl 1\n0\n0\n");

        assertEquals(1, fixture.voicingRepository.votes(mama.id()).size());
        assertTrue(printed().contains("Лайк"));
    }

    @Test
    void repeatedLikeRemovesVote() {
        Voicing mama = publishMama("sergey", 0);

        run("2\nn\n2\nl 1\nl 1\n0\n0\n");

        assertTrue(fixture.voicingRepository.votes(mama.id()).isEmpty());
    }

    @Test
    void votingForOwnVoicingIsRejectedWithExplanation() {
        publishMama("masha", 0);

        run("2\nn\n2\nl 1\n0\n0\n");

        assertTrue(printed().contains("свою"));
    }

    @Test
    void voiceCanBeSwitchedToText() {
        publishMama("sergey", 1);

        run("2\nn\n2\nt\ns\n");

        assertTrue(fixture.player.played().isEmpty());
    }

    @Test
    void samplePlaysFirstRecordedFragment() {
        publishMama("sergey", 1);

        run("2\nn\n2\np 1\n0\n0\n");

        assertEquals(1, fixture.player.played().size());
    }

    @Test
    void inputDuringPlaybackStopsIt() {
        publishMama("sergey", 1);

        run("2\ns\nстоп\n0\n");

        assertTrue(printed().contains("Остановлено"));
    }

    @Test
    void unknownCommandDoesNotBreakScreen() {
        run("2\nчто-то\n0\n");

        assertTrue(printed().contains("Не понимаю"));
    }

    @Test
    void authorIsShownByProfileNameNotId() {
        fixture.profile("Сергей");
        publishMama("сергей", 0);

        run("2\nn\n2\n0\ns\n");

        String printed = printed();
        assertTrue(printed.contains("Мама  Сергей"), () -> "в касте нет имени автора:\n" + printed);
        assertTrue(printed.contains("1  Сергей"), () -> "на экране выбора голоса нет имени:\n" + printed);
        assertTrue(printed.contains("[Мама · Сергей]"), () -> "при прослушивании нет имени:\n" + printed);
        assertFalse(printed.contains("сергей"), () -> "на экран просочился идентификатор:\n" + printed);
    }

    @Test
    void fragmentAndVoteCountsUseRussianPlurals() {
        publishMama("sergey", 0);

        run("2\nn\n2\nl 1\n0\n0\n");

        String printed = printed();
        assertTrue(printed.contains("Красная Шапочка — 21 фрагмент"), () -> printed);
        assertTrue(printed.contains("Мама — 1 фрагмент"), () -> printed);
        assertTrue(printed.contains("0 лайков, 0 дизлайков"), () -> printed);
        assertTrue(printed.contains("1 лайк, 0 дизлайков"), () -> printed);
    }
}
