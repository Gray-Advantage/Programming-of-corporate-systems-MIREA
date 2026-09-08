package space.grayt.teremok.cli;

import java.util.List;
import space.grayt.teremok.app.PlaybackStep;
import space.grayt.teremok.audio.AudioPlayer;

/** Проигрывание последовательности шагов сеанса. Наполняется в задаче 12. */
public final class PlaybackConsole {

    private final Console console;
    private final AudioPlayer player;

    public PlaybackConsole(Console console, AudioPlayer player) {
        this.console = console;
        this.player = player;
    }

    public void play(List<PlaybackStep> steps) {
    }
}
