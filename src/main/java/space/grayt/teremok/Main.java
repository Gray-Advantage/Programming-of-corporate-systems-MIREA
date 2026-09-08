package space.grayt.teremok;

import java.nio.file.Path;
import java.time.Clock;
import space.grayt.teremok.audio.JavaSoundPlayer;
import space.grayt.teremok.audio.JavaSoundRecorder;
import space.grayt.teremok.cli.Console;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Console console = new Console(System.in, System.out);
        new App(Path.of("data"), console, new JavaSoundRecorder(), new JavaSoundPlayer(), Clock.systemUTC()).run();
    }
}
