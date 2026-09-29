package space.grayt.teremok;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;
import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.ProfileService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.audio.AudioRecorder;
import space.grayt.teremok.cli.Console;
import space.grayt.teremok.cli.ListenFlow;
import space.grayt.teremok.cli.MainMenu;
import space.grayt.teremok.cli.MyVoicingsScreen;
import space.grayt.teremok.cli.PlaybackConsole;
import space.grayt.teremok.cli.ProfileScreen;
import space.grayt.teremok.cli.RecordFlow;
import space.grayt.teremok.db.DatabaseManager;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.exception.TeremokException;
import space.grayt.teremok.storage.AudioStorage;
import space.grayt.teremok.storage.JdbcProfileRepository;
import space.grayt.teremok.storage.JdbcVoicingRepository;
import space.grayt.teremok.storage.ProfileRepository;
import space.grayt.teremok.storage.StorageException;
import space.grayt.teremok.storage.VoicingRepository;
import space.grayt.teremok.textwork.TextWorkCatalog;

/**
 * Wires the layers — console screens, services, repositories over JDBC — and runs the outer
 * application loop.
 */
public final class App {

    private final DatabaseManager database;
    private final TextWorkCatalog textWorks;
    private final AudioStorage audio;
    private final Console console;
    private final AudioRecorder recorder;
    private final AudioPlayer player;
    private final Clock clock;
    private final UnaryOperator<Duration> pauseTransform;

    public App(DatabaseManager database, TextWorkCatalog textWorks, AudioStorage audio, Console console,
               AudioRecorder recorder, AudioPlayer player, Clock clock) {
        this(database, textWorks, audio, console, recorder, player, clock, UnaryOperator.identity());
    }

    /**
     * pauseTransform is applied to the reading pauses of unvoiced lines. It exists for tests only,
     * so the suite does not sleep in real time; the production Main uses the constructor without it.
     */
    public App(DatabaseManager database, TextWorkCatalog textWorks, AudioStorage audio, Console console,
               AudioRecorder recorder, AudioPlayer player, Clock clock, UnaryOperator<Duration> pauseTransform) {
        this.database = database;
        this.textWorks = textWorks;
        this.audio = audio;
        this.console = console;
        this.recorder = recorder;
        this.player = player;
        this.clock = clock;
        this.pauseTransform = pauseTransform;
    }

    public void run() {
        try {
            database.check();
        } catch (StorageException e) {
            console.println(e.getMessage());
            return;
        }

        try {
            textWorks.warnings().forEach(console::println);

            ProfileRepository profileRepository = new JdbcProfileRepository(database);
            VoicingRepository voicingRepository = new JdbcVoicingRepository(database);
            ProfileService profiles = new ProfileService(profileRepository);
            VotingService voting = new VotingService(voicingRepository);
            CastBuilder castBuilder = new CastBuilder(voting);
            VoicingService voicings = new VoicingService(voicingRepository, textWorks, audio, recorder, clock);
            PlaybackService playback = new PlaybackService(voicingRepository, audio);
            PlaybackConsole playbackConsole = new PlaybackConsole(console, player, profiles, pauseTransform);

            ProfileScreen profileScreen = new ProfileScreen(console, profiles);
            RecordFlow record = new RecordFlow(console, textWorks, voicings, player);
            ListenFlow listen = new ListenFlow(console, textWorks, castBuilder, voting, playback, playbackConsole,
                    player, profiles);
            MyVoicingsScreen mine = new MyVoicingsScreen(console, textWorks, voicings, voting, castBuilder, playback,
                    playbackConsole, record);
            MainMenu menu = new MainMenu(console, listen, record, mine);

            while (true) {
                Optional<Profile> profile = profileScreen.choose();
                if (profile.isEmpty() || menu.run(profile.get()) == MainMenu.MenuExit.QUIT) {
                    console.println("До встречи.");
                    return;
                }
            }
        } catch (TeremokException e) {
            // Own exceptions carry a message written for people, so print it as is and shut down
            // normally instead of letting a raw exception escape the cli layer.
            console.println(e.getMessage());
            console.println("Работа завершена.");
        } catch (RuntimeException e) {
            // Last line of defence: an unexpected error must not be printed as a raw stack trace.
            console.println("Произошла непредвиденная ошибка. Работа завершена.");
        }
    }
}
