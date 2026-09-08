package space.grayt.teremok.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ConsoleTest {

    private static Console console(String input, ByteArrayOutputStream out) {
        return new Console(new ByteArrayInputStream(input.getBytes(UTF_8)), out);
    }

    @Test
    void читаетСтрокиВUtf8ИОбрезаетПробелы() {
        Console console = console("  Сергей  \nМаша\n", new ByteArrayOutputStream());

        assertEquals("Сергей", console.readLine());
        assertEquals("Маша", console.readLine());
    }

    @Test
    void конецВводаРавнозначенКомандеВыхода() {
        assertEquals("0", console("", new ByteArrayOutputStream()).readLine());
    }

    @Test
    void печатаетКириллицуВUtf8() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        console("", out).println("Волк: Куда ты идёшь?");

        assertEquals("Волк: Куда ты идёшь?", out.toString(UTF_8).strip());
    }

    @Test
    void приглашениеПечатаетсяПередЧтением() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Console console = console("да\n", out);

        assertEquals("да", console.ask("> "));
        assertTrue(out.toString(UTF_8).startsWith("> "));
    }

    @Test
    void видитЧтоВводУжеЖдёт() {
        assertTrue(console("Enter\n", new ByteArrayOutputStream()).hasPendingInput());
        assertFalse(console("", new ByteArrayOutputStream()).hasPendingInput());
    }

    @Test
    void паузаПрерываетсяВводом() {
        Console withInput = console("\n", new ByteArrayOutputStream());
        Console withoutInput = console("", new ByteArrayOutputStream());

        assertFalse(withInput.sleepInterruptibly(Duration.ofMillis(300)));
        assertTrue(withoutInput.sleepInterruptibly(Duration.ofMillis(50)));
    }

    @Test
    void разбираетНомерПунктаМеню() {
        assertEquals(OptionalInt.of(0), Console.index("1", 3));
        assertEquals(OptionalInt.of(2), Console.index("3", 3));
        assertEquals(OptionalInt.empty(), Console.index("4", 3));
        assertEquals(OptionalInt.empty(), Console.index("0", 3));
        assertEquals(OptionalInt.empty(), Console.index("мусор", 3));
        assertEquals(OptionalInt.empty(), Console.index("", 3));
    }
}
