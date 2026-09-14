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
    void readsTitleAndLines() {
        Book book = BookParser.parse("shapochka", SOURCE);

        assertEquals("shapochka", book.id());
        assertEquals("Красная Шапочка", book.title());
        assertEquals(4, book.lines().size());
    }

    @Test
    void numbersLinesSequentiallySkippingBlankLines() {
        Book book = BookParser.parse("shapochka", SOURCE);

        assertEquals(List.of(1, 2, 3, 4), book.lines().stream().map(Line::number).toList());
        assertEquals("Куда ты идёшь?", book.lines().get(1).text());
    }

    @Test
    void collectsSpeakersInOrderOfAppearanceWithoutDuplicates() {
        Book book = BookParser.parse("shapochka", SOURCE);

        assertEquals(List.of("Рассказчик", "Волк", "Шапочка"),
                book.speakers().stream().map(Speaker::name).toList());
    }

    @Test
    void returnsLinesOfOneSpeaker() {
        Book book = BookParser.parse("shapochka", SOURCE);

        List<Line> wolf = book.linesOf(Speaker.idOf("Волк"));

        assertEquals(List.of(2, 4), wolf.stream().map(Line::number).toList());
    }

    @Test
    void speakerIdIsLowercaseWithoutSpaces() {
        assertEquals("красная-шапочка", Speaker.idOf("Красная Шапочка"));
    }

    @Test
    void missingTitleIsAnError() {
        String source = "---\nВолк: Привет.\n";

        BookFormatException error = assertThrows(BookFormatException.class,
                () -> BookParser.parse("bad", source));

        assertTrue(error.getMessage().contains("title"));
    }

    @Test
    void lineWithoutColonIsAnErrorWithLineNumber() {
        String source = "title: Тест\n---\nВолк: Привет.\nпросто текст\n";

        BookFormatException error = assertThrows(BookFormatException.class,
                () -> BookParser.parse("bad", source));

        assertEquals(4, error.lineNumber());
    }

    @Test
    void missingSeparatorIsAnError() {
        String source = "title: Тест\nВолк: Привет.\n";

        assertThrows(BookFormatException.class, () -> BookParser.parse("bad", source));
    }

    @Test
    void bookWithoutLinesIsAnError() {
        String source = "title: Тест\n---\n";

        assertThrows(BookFormatException.class, () -> BookParser.parse("bad", source));
    }

    @Test
    void exactly9999LinesParseSuccessfully() {
        StringBuilder source = new StringBuilder();
        source.append("title: Тест\n");
        source.append("---\n");
        for (int i = 1; i <= 9999; i++) {
            source.append("Персонаж: Реплика ").append(i).append("\n");
        }

        Book book = BookParser.parse("limit-test", source.toString());

        assertEquals(9999, book.lines().size());
    }

    @Test
    void tenThousandLinesThrow() {
        StringBuilder source = new StringBuilder();
        source.append("title: Тест\n");
        source.append("---\n");
        for (int i = 1; i <= 10000; i++) {
            source.append("Персонаж: Реплика ").append(i).append("\n");
        }

        BookFormatException error = assertThrows(BookFormatException.class,
                () -> BookParser.parse("limit-test", source.toString()));

        assertEquals(10002, error.lineNumber());
    }
}
