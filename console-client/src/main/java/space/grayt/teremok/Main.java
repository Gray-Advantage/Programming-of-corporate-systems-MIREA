package space.grayt.teremok;

import java.nio.file.Path;
import java.time.Clock;
import space.grayt.teremok.audio.JavaSoundPlayer;
import space.grayt.teremok.audio.JavaSoundRecorder;
import space.grayt.teremok.backend.BackendClient;
import space.grayt.teremok.cli.Console;
import space.grayt.teremok.textwork.BackendTextWorkCatalog;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Console console = Console.system();
        var textWorks = new BackendTextWorkCatalog(BackendClient.fromEnvironment());
        new App(
                Path.of("data"),
                console,
                new JavaSoundRecorder(),
                new JavaSoundPlayer(),
                Clock.systemUTC(),
                textWorks).run();
    }
}
