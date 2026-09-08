package space.grayt.teremok.book;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import space.grayt.teremok.domain.Book;

class BookLibraryTest {

    private final BookLibrary library = new BookLibrary();

    @Test
    void загружаетВсеВстроенныеКниги() {
        assertEquals(3, library.all().size());
        assertTrue(library.warnings().isEmpty(), () -> String.join("; ", library.warnings()));
    }

    @Test
    void укаждойКнигиЕстьЗаголовокРепликиИПерсонажи() {
        for (Book book : library.all()) {
            assertFalse(book.title().isBlank());
            assertFalse(book.lines().isEmpty());
            assertTrue(book.speakers().size() >= 2, book.id());
        }
    }

    @Test
    void находитКнигуПоИдентификатору() {
        assertEquals("Теремок", library.find("teremok").orElseThrow().title());
    }

    @Test
    void неизвестнаяКнигаНеНаходится() {
        assertTrue(library.find("нет-такой").isEmpty());
    }

    @Test
    void номераРепликСквозныеИНачинаютсяСЕдиницы() {
        Book book = library.find("kolobok").orElseThrow();

        assertEquals(1, book.lines().get(0).number());
        assertEquals(book.lines().size(), book.lines().get(book.lines().size() - 1).number());
    }

    @Test
    void идентификаторыКнигУникальны() {
        List<String> ids = library.all().stream().map(Book::id).toList();

        assertEquals(ids.size(), ids.stream().distinct().count());
    }
}
