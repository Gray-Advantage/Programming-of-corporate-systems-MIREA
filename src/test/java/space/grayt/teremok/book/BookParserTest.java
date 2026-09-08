package space.grayt.teremok.book;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Line;
import space.grayt.teremok.domain.Speaker;

class BookParserTest {

    private static final String SOURCE = """
            title: Красная Шапочка
            ---
            Рассказчик: Жила-была девочка.

            Волк: Куда ты идёшь?
            Шапочка: К бабушке.
            Волк: А где живёт бабушка?
            """;

    @Test
    void читаетЗаголовокИРеплики() {
        Book book = BookParser.parse("shapochka", SOURCE);

        assertEquals("shapochka", book.id());
        assertEquals("Красная Шапочка", book.title());
        assertEquals(4, book.lines().size());
    }

    @Test
    void нумеруетРепликиПодрядИгнорируяПустыеСтроки() {
        Book book = BookParser.parse("shapochka", SOURCE);

        assertEquals(List.of(1, 2, 3, 4), book.lines().stream().map(Line::number).toList());
        assertEquals("Куда ты идёшь?", book.lines().get(1).text());
    }

    @Test
    void собираетПерсонажейВПорядкеПоявленияБезДублей() {
        Book book = BookParser.parse("shapochka", SOURCE);

        assertEquals(List.of("Рассказчик", "Волк", "Шапочка"),
                book.speakers().stream().map(Speaker::name).toList());
    }

    @Test
    void отдаётРепликиОдногоПерсонажа() {
        Book book = BookParser.parse("shapochka", SOURCE);

        List<Line> волк = book.linesOf(Speaker.idOf("Волк"));

        assertEquals(List.of(2, 4), волк.stream().map(Line::number).toList());
    }

    @Test
    void идентификаторПерсонажаБезПробеловИВНижнемРегистре() {
        assertEquals("красная-шапочка", Speaker.idOf("Красная Шапочка"));
    }

    @Test
    void отсутствиеЗаголовкаОшибка() {
        String source = "---\nВолк: Привет.\n";

        BookFormatException error = assertThrows(BookFormatException.class,
                () -> BookParser.parse("bad", source));

        assertTrue(error.getMessage().contains("title"));
    }

    @Test
    void строкаБезДвоеточияОшибкаСНомером() {
        String source = "title: Тест\n---\nВолк: Привет.\nпросто текст\n";

        BookFormatException error = assertThrows(BookFormatException.class,
                () -> BookParser.parse("bad", source));

        assertEquals(4, error.lineNumber());
    }

    @Test
    void отсутствиеРазделителяОшибка() {
        String source = "title: Тест\nВолк: Привет.\n";

        assertThrows(BookFormatException.class, () -> BookParser.parse("bad", source));
    }

    @Test
    void книгаБезРепликОшибка() {
        String source = "title: Тест\n---\n";

        assertThrows(BookFormatException.class, () -> BookParser.parse("bad", source));
    }
}
