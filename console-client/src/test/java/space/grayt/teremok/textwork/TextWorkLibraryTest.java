package space.grayt.teremok.textwork;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.domain.TextWork;

class TextWorkLibraryTest {

    private final TextWorkLibrary library = new TextWorkLibrary();

    @Test
    void loadsAllBundledBooks() {
        assertEquals(3, library.all().size());
        assertTrue(library.warnings().isEmpty(), () -> String.join("; ", library.warnings()));
    }

    @Test
    void everyBookHasTitleLinesAndSpeakers() {
        for (TextWork book : library.all()) {
            assertFalse(book.title().isBlank());
            assertFalse(book.fragments().isEmpty());
            assertTrue(book.voiceParts().size() >= 2, book.id());
        }
    }

    @Test
    void findsBookById() {
        assertEquals("Теремок", library.find("teremok").orElseThrow().title());
    }

    @Test
    void unknownBookIsNotFound() {
        assertTrue(library.find("нет-такой").isEmpty());
    }

    @Test
    void lineNumbersAreSequentialFromOne() {
        TextWork book = library.find("kolobok").orElseThrow();

        assertEquals(1, book.fragments().get(0).number());
        assertEquals(book.fragments().size(), book.fragments().get(book.fragments().size() - 1).number());
    }

    @Test
    void bookIdsAreUnique() {
        List<String> ids = library.all().stream().map(TextWork::id).toList();

        assertEquals(ids.size(), ids.stream().distinct().count());
    }
}
