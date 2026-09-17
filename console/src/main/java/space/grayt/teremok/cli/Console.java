package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.time.Duration;
import java.util.OptionalInt;

/** Ввод-вывод приложения: в кодировке терминала либо в явно заданной кодировке потока. */
public final class Console {

    private static final long POLL_MILLIS = 50;
    private static final String EXIT = "0";

    private final BufferedReader in;
    private final PrintWriter out;
    private boolean closed;

    /** Создаёт консоль для UTF-8-потоков, например файлов, пайпов и тестовых буферов. */
    public Console(InputStream in, OutputStream out) {
        this(new InputStreamReader(in, UTF_8), new OutputStreamWriter(out, UTF_8));
    }

    /** Создаёт консоль поверх символьных потоков, кодировка которых уже выбрана вызывающим кодом. */
    public Console(Reader in, Writer out) {
        this.in = in instanceof BufferedReader buffered ? buffered : new BufferedReader(in);
        this.out = out instanceof PrintWriter printWriter ? printWriter : new PrintWriter(out, true);
    }

    /**
     * Использует кодировку реального терминала. При запуске через IDE, Gradle или пайп входным
     * и выходным протоколом считается UTF-8.
     */
    public static Console system() {
        java.io.Console terminal = System.console();
        if (terminal != null) {
            return new Console(terminal.reader(), terminal.writer());
        }
        return new Console(System.in, System.out);
    }

    public void print(String text) {
        out.print(text);
        out.flush();
    }

    public void println(String text) {
        out.println(text);
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
