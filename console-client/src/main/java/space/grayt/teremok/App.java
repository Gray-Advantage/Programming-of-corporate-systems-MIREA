package space.grayt.teremok;

import space.grayt.teremok.app.CastBuilder;
import space.grayt.teremok.app.PlaybackService;
import space.grayt.teremok.app.VoicingService;
import space.grayt.teremok.app.VotingService;
import space.grayt.teremok.audio.AudioPlayer;
import space.grayt.teremok.audio.AudioRecorder;
import space.grayt.teremok.cli.*;
import space.grayt.teremok.domain.Profile;
import space.grayt.teremok.storage.*;
import space.grayt.teremok.textwork.TextWorkCatalog;
import space.grayt.teremok.textwork.TextWorkLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Wires the dependencies and runs the outer application loop.
 */
public final class App {

    private final Path dataDir;
    private final Console console;
    private final AudioRecorder recorder;
    private final AudioPlayer player;
    private final Clock clock;
    private final UnaryOperator<Duration> pauseTransform;
    private final TextWorkCatalog textWorks;

    public App(Path dataDir, Console console, AudioRecorder recorder, AudioPlayer player, Clock clock) {
        this(dataDir, console, recorder, player, clock, UnaryOperator.identity(), new TextWorkLibrary());
    }

    public App(Path dataDir, Console console, AudioRecorder recorder, AudioPlayer player, Clock clock, TextWorkCatalog textWorks) {
        this(dataDir, console, recorder, player, clock, UnaryOperator.identity(), textWorks);
    }

    /**
     * pauseTransform is applied to the reading pauses of unvoiced lines. It exists for tests only,
     * so the suite does not sleep in real time; the production Main uses the constructor without it.
     */
    public App(Path dataDir, Console console, AudioRecorder recorder, AudioPlayer player, Clock clock,
               UnaryOperator<Duration> pauseTransform) {
        this(dataDir, console, recorder, player, clock, pauseTransform, new TextWorkLibrary());
    }

    App(Path dataDir, Console console, AudioRecorder recorder, AudioPlayer player, Clock clock,
        UnaryOperator<Duration> pauseTransform, TextWorkCatalog textWorks) {
        this.dataDir = dataDir;
        this.console = console;
        this.recorder = recorder;
        this.player = player;
        this.clock = clock;
        this.pauseTransform = pauseTransform;
        this.textWorks = textWorks;
    }

    public void run() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            console.println("Нет доступа к каталогу данных " + dataDir + ": " + e.getMessage());
            return;
        }

        textWorks.warnings().forEach(console::println);

        ProfileRepository profiles = new FileProfileRepository(dataDir);
        VoicingRepository voicings = new FileVoicingRepository(dataDir);
        VotingService voting = new VotingService(voicings);
        CastBuilder castBuilder = new CastBuilder(voting);
        VoicingService voicingService = new VoicingService(voicings, recorder, clock);
        PlaybackService playback = new PlaybackService(voicings);
        PlaybackConsole playbackConsole = new PlaybackConsole(console, player, profiles, pauseTransform);

        ProfileScreen profileScreen = new ProfileScreen(console, profiles);
        RecordFlow record = new RecordFlow(console, textWorks, voicingService, player);
        ListenFlow listen = new ListenFlow(console, textWorks, castBuilder, voting, playback, playbackConsole, player, profiles);
        MyVoicingsScreen mine = new MyVoicingsScreen(console, textWorks, voicingService, voting, castBuilder, playback, playbackConsole, record);
        MainMenu menu = new MainMenu(console, listen, record, mine, voicings);

        try {
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
        } catch (StorageException e) {
            // StorageException messages are already written for people (spec §8), so print them as is
            // and shut down normally instead of letting a raw exception escape the cli layer.
            console.println(e.getMessage());
            console.println("Работа завершена.");
        } catch (RuntimeException e) {
            // Last line of defence: an unexpected error must not be printed as a raw stack trace.
            console.println("Произошла непредвиденная ошибка. Работа завершена.");
        }
    }
}
