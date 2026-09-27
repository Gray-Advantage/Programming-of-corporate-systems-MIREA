package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Console input and output in the terminal charset. Hard-coded UTF-8 broke Windows, whose console
 * uses cp866 by default and turned Cyrillic into garbage both ways. Characters the terminal charset
 * cannot encode are replaced with close ASCII instead of question marks.
 */
public final class Console {

    private static final long POLL_MILLIS = 50;
    private static final String EXIT = "0";
    private static final Map<Integer, String> FALLBACK = Map.of(
            (int) '—', "-",
            (int) '–', "-",
            (int) '«', "\"",
            (int) '»', "\"",
            (int) '·', "-",
            (int) '●', "*",
            (int) '…', "...");

    private final BufferedReader in;
    private final PrintStream out;
    private final CharsetEncoder encoder;
    private boolean closed;

    /** Console over UTF-8 streams: files, pipes and test buffers. */
    public Console(InputStream in, OutputStream out) {
        this(in, out, UTF_8);
    }

    public Console(InputStream in, OutputStream out, Charset charset) {
        this.in = new BufferedReader(new InputStreamReader(in, charset));
        this.out = new PrintStream(out, true, charset);
        this.encoder = charset.newEncoder();
    }

    /**
     * Console over the standard streams in the charset of the real terminal. Without a terminal
     * (IDE, Gradle, pipe) the streams are treated as UTF-8.
     */
    public static Console system() {
        java.io.Console terminal = System.console();
        return new Console(System.in, System.out, terminal != null ? terminal.charset() : UTF_8);
    }

    public void print(String text) {
        out.print(printable(text));
        out.flush();
    }

    public void println(String text) {
        out.println(printable(text));
    }

    public void println() {
        out.println();
    }

    public String ask(String prompt) {
        print(prompt);
        return readLine();
    }

    /** End of input (Ctrl+D or an exhausted stream) counts as 0, otherwise menus would loop forever. */
    public String readLine() {
        try {
            String line = in.readLine();
            if (line == null) {
                closed = true;
                return EXIT;
            }
            return line.trim();
        } catch (IOException e) {
            closed = true;
            return EXIT;
        }
    }

    /** True once the input stream has closed; the 0 returned by readLine() is then not real input. */
    public boolean isClosed() {
        return closed;
    }

    public boolean hasPendingInput() {
        try {
            return in.ready();
        } catch (IOException e) {
            return false;
        }
    }

    /** Sleeps for the given time. Returns false if the user interrupted the pause with input. */
    public boolean sleepInterruptibly(Duration duration) {
        long deadline = System.currentTimeMillis() + duration.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (hasPendingInput()) {
                return false;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !hasPendingInput();
    }

    private String printable(String text) {
        if (encoder.canEncode(text)) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            String symbol = Character.toString(codePoint);
            result.append(encoder.canEncode(symbol) ? symbol : FALLBACK.getOrDefault(codePoint, symbol));
        });
        return result.toString();
    }

    /** Turns input 3 into index 2. Empty when the input is not a menu item number. */
    public static OptionalInt index(String input, int size) {
        try {
            int number = Integer.parseInt(input.trim());
            return number >= 1 && number <= size ? OptionalInt.of(number - 1) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
