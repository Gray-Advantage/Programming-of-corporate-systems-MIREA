package space.grayt.teremok.textwork;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.VoicePart;

class TextWorkParserTest {

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
        TextWork book = TextWorkParser.parse("shapochka", SOURCE);

        assertEquals("shapochka", book.id());
        assertEquals("Красная Шапочка", book.title());
        assertEquals(4, book.fragments().size());
    }

    @Test
    void numbersLinesSequentiallySkippingBlankLines() {
        TextWork book = TextWorkParser.parse("shapochka", SOURCE);

        assertEquals(List.of(1, 2, 3, 4), book.fragments().stream().map(TextWorkFragment::number).toList());
        assertEquals("Куда ты идёшь?", book.fragments().get(1).text());
    }

    @Test
    void collectsSpeakersInOrderOfAppearanceWithoutDuplicates() {
        TextWork book = TextWorkParser.parse("shapochka", SOURCE);

        assertEquals(List.of("Рассказчик", "Волк", "Шапочка"),
                book.voiceParts().stream().map(VoicePart::name).toList());
    }

    @Test
    void returnsLinesOfOneSpeaker() {
        TextWork book = TextWorkParser.parse("shapochka", SOURCE);

        List<TextWorkFragment> wolf = book.fragmentsOf(VoicePart.idOf("Волк"));

        assertEquals(List.of(2, 4), wolf.stream().map(TextWorkFragment::number).toList());
    }

    @Test
    void speakerIdIsLowercaseWithoutSpaces() {
        assertEquals("красная-шапочка", VoicePart.idOf("Красная Шапочка"));
    }

    @Test
    void missingTitleIsAnError() {
        String source = "---\nВолк: Привет.\n";

        TextWorkFormatException error = assertThrows(TextWorkFormatException.class,
                () -> TextWorkParser.parse("bad", source));

        assertTrue(error.getMessage().contains("title"));
    }

    @Test
    void lineWithoutColonIsAnErrorWithLineNumber() {
        String source = "title: Тест\n---\nВолк: Привет.\nпросто текст\n";

        TextWorkFormatException error = assertThrows(TextWorkFormatException.class,
                () -> TextWorkParser.parse("bad", source));

        assertEquals(4, error.lineNumber());
    }

    @Test
    void missingSeparatorIsAnError() {
        String source = "title: Тест\nВолк: Привет.\n";

        assertThrows(TextWorkFormatException.class, () -> TextWorkParser.parse("bad", source));
    }

    @Test
    void bookWithoutLinesIsAnError() {
        String source = "title: Тест\n---\n";

        assertThrows(TextWorkFormatException.class, () -> TextWorkParser.parse("bad", source));
    }

    @Test
    void exactly9999LinesParseSuccessfully() {
        StringBuilder source = new StringBuilder();
        source.append("title: Тест\n");
        source.append("---\n");
        for (int i = 1; i <= 9999; i++) {
            source.append("Персонаж: Реплика ").append(i).append("\n");
        }

        TextWork book = TextWorkParser.parse("limit-test", source.toString());

        assertEquals(9999, book.fragments().size());
    }

    @Test
    void tenThousandLinesThrow() {
        StringBuilder source = new StringBuilder();
        source.append("title: Тест\n");
        source.append("---\n");
        for (int i = 1; i <= 10000; i++) {
            source.append("Персонаж: Реплика ").append(i).append("\n");
        }

        TextWorkFormatException error = assertThrows(TextWorkFormatException.class,
                () -> TextWorkParser.parse("limit-test", source.toString()));

        assertEquals(10002, error.lineNumber());
    }
}
