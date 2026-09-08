package space.grayt.teremok.cli;

import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Profile;

/** Экран прослушивания книги. Наполняется в задаче 12. */
public final class ListenFlow {

    private final Console console;
    private final BookLibrary books;
    private final CastBuilder castBuilder;
    private final VotingService voting;
    private final PlaybackService playback;
    private final PlaybackConsole playbackConsole;
    private final AudioPlayer player;

    public ListenFlow(Console console, BookLibrary books, CastBuilder castBuilder, VotingService voting,
            PlaybackService playback, PlaybackConsole playbackConsole, AudioPlayer player) {
        this.console = console;
        this.books = books;
        this.castBuilder = castBuilder;
        this.voting = voting;
        this.playback = playback;
        this.playbackConsole = playbackConsole;
        this.player = player;
    }

    public void run(Profile profile) {
    }
}
