package space.grayt.teremok.cli;

import java.util.List;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.PlaybackStep;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.audio.AudioUnavailableException;

/** Проигрывает готовый план: звук там, где он есть, текст с паузой там, где его нет. */
public final class PlaybackConsole {

    private final Console console;
    private final AudioPlayer player;

    public PlaybackConsole(Console console, AudioPlayer player) {
        this.console = console;
        this.player = player;
    }

    public void play(List<PlaybackStep> steps) {
        console.println();
        console.println("Идёт воспроизведение. Enter — остановить.");
        console.println();
        for (PlaybackStep step : steps) {
            String voice = step.isSpoken() ? step.authorId() : "текстом";
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
            return console.sleepInterruptibly(PlaybackService.readingPause(step.line().text()));
        }
        try {
            player.play(step.audio());
        } catch (AudioUnavailableException e) {
            console.println("  " + e.getMessage());
        }
        return !console.hasPendingInput();
    }
}
