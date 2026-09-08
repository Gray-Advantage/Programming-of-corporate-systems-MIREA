package space.grayt.teremok;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.audio.AudioRecorder;
import space.grayt.teremok.book.BookLibrary;
import space.grayt.teremok.cli.Console;
import space.grayt.teremok.cli.ListenFlow;
import space.grayt.teremok.cli.MainMenu;
import space.grayt.teremok.cli.MyVoicingsScreen;
import space.grayt.teremok.cli.PlaybackConsole;
import space.grayt.teremok.cli.ProfileScreen;
import space.grayt.teremok.cli.RecordFlow;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.FileProfileRepository;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.ProfileRepository;
import space.grayt.teremok.storage.VoicingRepository;

/** Проводка зависимостей и внешний цикл приложения. */
public final class App {

    private final Path dataDir;
    private final Console console;
    private final AudioRecorder recorder;
    private final AudioPlayer player;
    private final Clock clock;

    public App(Path dataDir, Console console, AudioRecorder recorder, AudioPlayer player, Clock clock) {
        this.dataDir = dataDir;
        this.console = console;
        this.recorder = recorder;
        this.player = player;
        this.clock = clock;
    }

    public void run() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            console.println("Нет доступа к каталогу данных " + dataDir + ": " + e.getMessage());
            return;
        }

        BookLibrary books = new BookLibrary();
        books.warnings().forEach(console::println);

        ProfileRepository profiles = new FileProfileRepository(dataDir);
        VoicingRepository voicings = new FileVoicingRepository(dataDir);
        VotingService voting = new VotingService(voicings);
        CastBuilder castBuilder = new CastBuilder(voting);
        VoicingService voicingService = new VoicingService(voicings, recorder, clock);
        PlaybackService playback = new PlaybackService(voicings);
        PlaybackConsole playbackConsole = new PlaybackConsole(console, player);

        ProfileScreen profileScreen = new ProfileScreen(console, profiles);
        RecordFlow record = new RecordFlow(console, books, voicingService, player);
        ListenFlow listen = new ListenFlow(console, books, castBuilder, voting, playback, playbackConsole, player);
        MyVoicingsScreen mine = new MyVoicingsScreen(console, books, voicingService, voting, castBuilder,
                playback, playbackConsole, record);
        MainMenu menu = new MainMenu(console, listen, record, mine);

        while (true) {
            Optional<Profile> profile = profileScreen.choose();
            if (profile.isEmpty()) {
                console.println("До встречи.");
                return;
            }
            if (menu.run(profile.get()) == MainMenu.MenuExit.QUIT) {
                console.println("До встречи.");
                return;
            }
        }
    }
}
