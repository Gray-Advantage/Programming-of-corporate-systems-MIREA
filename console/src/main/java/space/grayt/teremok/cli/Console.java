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
 * Ввод-вывод консоли в кодировке терминала. Жёсткий UTF-8 ломал Windows: её консоль по умолчанию
 * работает в cp866, и кириллица превращалась в кашу в обе стороны. Символы, которых в кодировке
 * терминала нет, заменяются близкими ASCII, а не вопросительными знаками.
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

    public Console(InputStream in, OutputStream out) {
        this(in, out, UTF_8);
    }

    public Console(InputStream in, OutputStream out, Charset charset) {
        this.in = new BufferedReader(new InputStreamReader(in, charset));
        this.out = new PrintStream(out, true, charset);
        this.encoder = charset.newEncoder();
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

    /** Конец ввода — Ctrl+D или исчерпанный поток — равнозначен «0», иначе меню зациклится. */
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

    /** Истинно, если поток ввода уже закрылся — «0» из readLine() в этом случае не настоящий ввод. */
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

    /** Спит указанное время. Возвращает false, если пользователь прервал паузу вводом. */
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

    /** Превращает ввод «3» в индекс 2. Пустой результат — ввод не является номером пункта. */
    public static OptionalInt index(String input, int size) {
        try {
            int number = Integer.parseInt(input.trim());
            return number >= 1 && number <= size ? OptionalInt.of(number - 1) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
