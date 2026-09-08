package space.grayt.teremok.cli;

import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Profile;

/** Экран записи роли. Наполняется в задаче 13. */
public final class RecordFlow {

    private final Console console;
    private final BookLibrary books;
    private final VoicingService voicings;
    private final AudioPlayer player;

    public RecordFlow(Console console, BookLibrary books, VoicingService voicings, AudioPlayer player) {
        this.console = console;
        this.books = books;
        this.voicings = voicings;
        this.player = player;
    }

    public void run(Profile profile) {
    }
}
