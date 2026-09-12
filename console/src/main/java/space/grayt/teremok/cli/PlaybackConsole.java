package space.grayt.teremok.cli;

import java.time.Duration;
import java.util.List;
import java.util.function.UnaryOperator;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.PlaybackStep;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.audio.AudioUnavailableException;
import space.grayt.teremok.storage.ProfileRepository;

/** Проигрывает готовый план: звук там, где он есть, текст с паузой там, где его нет. */
public final class PlaybackConsole {

    private final Console console;
    private final AudioPlayer player;
    private final ProfileRepository profiles;
    private final UnaryOperator<Duration> pauseTransform;

    public PlaybackConsole(Console console, AudioPlayer player, ProfileRepository profiles) {
        this(console, player, profiles, UnaryOperator.identity());
    }

    /**
     * pauseTransform применяется к паузе чтения перед сном — тестам, чтобы не ждать реальное
     * время, боевому коду не нужен: {@link #PlaybackConsole(Console, AudioPlayer, ProfileRepository)} передаёт сюда
     * тождественное преобразование.
     */
    public PlaybackConsole(Console console, AudioPlayer player, ProfileRepository profiles,
            UnaryOperator<Duration> pauseTransform) {
        this.console = console;
        this.player = player;
        this.profiles = profiles;
        this.pauseTransform = pauseTransform;
    }

    public void play(List<PlaybackStep> steps) {
        console.println();
        console.println("Идёт воспроизведение. Enter — остановить.");
        console.println();
        for (PlaybackStep step : steps) {
            String voice = step.isSpoken() ? profiles.nameOf(step.authorId()) : "текстом";
            console.println("[" + step.speakerName() + " · " + voice + "] " + step.line().text());
            if (!playStep(step)) {
                console.readLine();
                console.println("Остановлено.");
                return;
            }
        }
        console.println();
        console.println("Книга закончилась.");
    }

    /** false означает, что пользователь прервал воспроизведение. */
    private boolean playStep(PlaybackStep step) {
        if (!step.isSpoken()) {
            return console.sleepInterruptibly(pauseTransform.apply(PlaybackService.readingPause(step.line().text())));
        }
        try {
            player.play(step.audio());
        } catch (AudioUnavailableException e) {
            console.println("  " + e.getMessage());
        }
        return !console.hasPendingInput();
    }
}
