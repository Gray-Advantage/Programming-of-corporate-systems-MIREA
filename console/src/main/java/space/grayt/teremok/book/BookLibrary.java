package space.grayt.teremok.book;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import space.grayt.teremok.domain.Book;

/** Bundled books loaded from resources. A book that fails to parse is skipped. */
public final class BookLibrary {

    public static final List<String> BOOK_IDS = List.of("shapochka", "teremok", "kolobok");

    private final Map<String, Book> books = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public BookLibrary() {
        for (String id : BOOK_IDS) {
            try {
                books.put(id, BookParser.parse(id, read(id)));
            } catch (BookFormatException e) {
                warnings.add("Книга " + id + " пропущена, строка " + e.lineNumber() + ": " + e.getMessage());
            } catch (IOException e) {
                warnings.add("Книга " + id + " не прочитана: " + e.getMessage());
            }
        }
    }

    public List<Book> all() {
        return List.copyOf(books.values());
    }

    public Optional<Book> find(String bookId) {
        return Optional.ofNullable(books.get(bookId));
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private static String read(String bookId) throws IOException {
        String resource = "/books/" + bookId + ".txt";
        try (InputStream stream = BookLibrary.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("ресурс " + resource + " не найден");
            }
            return new String(stream.readAllBytes(), UTF_8);
        }
    }
}
