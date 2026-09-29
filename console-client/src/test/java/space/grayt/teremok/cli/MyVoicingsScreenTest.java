package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.Fixture;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.VoteKind;

class MyVoicingsScreenTest {

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

    /** The Mother voicing with its only fragment 2, fully recorded. */
    private Voicing completeMama(VoicingStatus status) {
        return fixture.voicing(shapochka, "Мама", "sergey", status);
    }

    /** A draft of a Little Red Riding Hood voice part with its first fragments recorded. */
    private Voicing draft(String voicePartName, int recorded) {
        return fixture.recordFirst(fixture.draft(shapochka, voicePartName, "sergey"), shapochka, recorded);
    }

    private VoicingStatus statusOf(Voicing voicing) {
        return fixture.reload(voicing).status();
    }

    private void run(String input) {
        out = new ByteArrayOutputStream();
        Console console = new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
        VoicingService voicings = fixture.voicings();
        VotingService voting = fixture.voting();
        // Zero reading pause: the test must not actually wait for unvoiced fragments.
        PlaybackConsole playbackConsole = new PlaybackConsole(console, fixture.player, fixture.profiles(),
                duration -> Duration.ZERO);
        RecordFlow record = new RecordFlow(console, fixture.textWorks, voicings, fixture.player);
        new MyVoicingsScreen(console, fixture.textWorks, voicings, voting, fixture.castBuilder(), fixture.playback(),
                playbackConsole, record).run(sergey);
    }

    private String printed() {
        return out.toString(UTF_8);
    }

    @Test
    void emptyListSaysThereAreNoVoicings() {
        run("0\n");

        assertTrue(printed().contains("Вы пока ничего не озвучивали"));
    }

    @Test
    void showsTextWorkVoicePartStatusProgressAndScore() {
        completeMama(VoicingStatus.DRAFT);

        run("0\n");

        assertTrue(printed().contains("Красная Шапочка · Мама  черновик  1/1  [0]"), this::printed);
    }

    @Test
    void statusesAreShownInRussian() {
        completeMama(VoicingStatus.PUBLISHED);
        fixture.withStatus(draft("Бабушка", 2), VoicingStatus.ARCHIVED);
        draft("Волк", 1);

        run("0\n");

        assertTrue(printed().contains("Мама  опубликовано"), this::printed);
        assertTrue(printed().contains("Бабушка  снято с публикации"), this::printed);
        assertTrue(printed().contains("Волк  черновик"), this::printed);
    }

    @Test
    void publishesCompleteVoicing() {
        Voicing mama = completeMama(VoicingStatus.DRAFT);

        run("1\n3\n0\n0\n");

        assertEquals(VoicingStatus.PUBLISHED, statusOf(mama));
        assertTrue(printed().contains("Роль опубликована."), this::printed);
    }

    @Test
    void incompleteVoicingIsNotPublished() {
        Voicing hood = draft("Шапочка", 0);

        run("1\n3\n0\n0\n");

        assertEquals(VoicingStatus.DRAFT, statusOf(hood));
        assertTrue(printed().contains("полностью записанную"), this::printed);
    }

    @Test
    void publishedVoicingIsTakenDownIntoArchiveWithVotesKept() {
        Voicing mama = completeMama(VoicingStatus.PUBLISHED);
        fixture.vote(mama, VoteKind.LIKE, "masha");

        run("1\n3\n0\n0\n");

        assertEquals(VoicingStatus.ARCHIVED, statusOf(mama));
        assertEquals(1, fixture.voicingRepository.votes(mama.id()).size());
        assertTrue(printed().contains("3  Снять с публикации"), this::printed);
        assertTrue(printed().contains("Роль снята с публикации"), this::printed);
    }

    @Test
    void archivedVoicingIsReturnedToPublication() {
        Voicing mama = completeMama(VoicingStatus.ARCHIVED);

        run("1\n3\n0\n0\n");

        assertEquals(VoicingStatus.PUBLISHED, statusOf(mama));
        assertTrue(printed().contains("3  Вернуть в публикацию"), this::printed);
        assertTrue(printed().contains("Роль снова опубликована."), this::printed);
    }

    @Test
    void playsWholeVoicingEvenAsDraft() {
        completeMama(VoicingStatus.DRAFT);

        run("1\n2\n");

        assertEquals(1, fixture.player.played().size());
    }

    @Test
    void deletesDraftAfterConfirmation() {
        Voicing mama = completeMama(VoicingStatus.DRAFT);

        run("1\n4\nда\n0\n");

        assertTrue(fixture.voicingRepository.find(mama.id()).isEmpty());
        assertFalse(Files.exists(fixture.audio.fileFor(mama.id(), 2).getParent()));
        assertTrue(printed().contains("Роль удалена."), this::printed);
    }

    @Test
    void deletesArchivedVoicingAfterConfirmation() {
        Voicing mama = completeMama(VoicingStatus.ARCHIVED);

        run("1\n4\nда\n0\n");

        assertTrue(fixture.voicingRepository.find(mama.id()).isEmpty());
    }

    @Test
    void publishedVoicingIsNotDeleted() {
        Voicing mama = completeMama(VoicingStatus.PUBLISHED);

        run("1\n4\n0\n0\n");

        assertTrue(fixture.voicingRepository.find(mama.id()).isPresent());
        assertTrue(printed().contains("Сначала снимите её с публикации"), this::printed);
        assertFalse(printed().contains("да / нет"), this::printed);
    }

    @Test
    void voicingStaysWithoutConfirmation() {
        Voicing mama = completeMama(VoicingStatus.DRAFT);

        run("1\n4\nнет\n0\n0\n");

        assertTrue(fixture.voicingRepository.find(mama.id()).isPresent());
    }

    @Test
    void publishAllPublishesEveryFullyRecordedVoicing() {
        Voicing mama = draft("Мама", 1);
        Voicing grandma = draft("Бабушка", 2);
        Voicing hood = draft("Шапочка", 1);

        run("a\n0\n");

        assertEquals(VoicingStatus.PUBLISHED, statusOf(mama), this::printed);
        assertEquals(VoicingStatus.PUBLISHED, statusOf(grandma), this::printed);
        assertEquals(VoicingStatus.DRAFT, statusOf(hood), this::printed);
        assertTrue(printed().contains("Опубликовано: 2 роли"), this::printed);
        assertTrue(printed().contains("Шапочка  черновик  1/7"), this::printed);
    }

    @Test
    void publishAllShowsNumberOfReadyDrafts() {
        draft("Мама", 1);
        draft("Бабушка", 2);
        fixture.withStatus(draft("Волк", 6), VoicingStatus.PUBLISHED);
        draft("Шапочка", 1);

        run("0\n");

        assertTrue(printed().contains("a  Опубликовать все готовые (2)"), this::printed);
    }

    @Test
    void publishAllIsHiddenWithoutReadyDrafts() {
        draft("Шапочка", 1);

        run("0\n");

        assertFalse(printed().contains("Опубликовать все"), this::printed);
    }
}
