package space.grayt.teremok.cli;

import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.domain.Profile;

/** Экран «Мои озвучки»: свои роли, публикация, удаление. Наполняется в задаче 14. */
public final class MyVoicingsScreen {

    private final Console console;
    private final BookLibrary books;
    private final VoicingService voicings;
    private final VotingService voting;
    private final CastBuilder castBuilder;
    private final PlaybackService playback;
    private final PlaybackConsole playbackConsole;
    private final RecordFlow record;

    public MyVoicingsScreen(Console console, BookLibrary books, VoicingService voicings, VotingService voting,
            CastBuilder castBuilder, PlaybackService playback, PlaybackConsole playbackConsole, RecordFlow record) {
        this.console = console;
        this.books = books;
        this.voicings = voicings;
        this.voting = voting;
        this.castBuilder = castBuilder;
        this.playback = playback;
        this.playbackConsole = playbackConsole;
        this.record = record;
    }

    public void run(Profile profile) {
    }
}
