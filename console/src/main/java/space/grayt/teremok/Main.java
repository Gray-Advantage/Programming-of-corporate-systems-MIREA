package space.grayt.teremok;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import space.grayt.teremok.audio.JavaSoundPlayer;
import space.grayt.teremok.audio.JavaSoundRecorder;
import space.grayt.teremok.cli.Console;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Console console = new Console(System.in, System.out, terminalCharset());
        new App(Path.of("data"), console, new JavaSoundRecorder(), new JavaSoundPlayer(), Clock.systemUTC()).run();
    }

    /** Charset of the terminal running the app; UTF-8 when there is no terminal (IDE, pipe). */
    private static Charset terminalCharset() {
        java.io.Console terminal = System.console();
        return terminal != null ? terminal.charset() : StandardCharsets.UTF_8;
    }
}
