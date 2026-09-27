package space.grayt.teremok.book;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Line;
import space.grayt.teremok.domain.Speaker;

/** Parses a book: header, a --- separator, then lines in the form Name: text. */
public final class BookParser {

    private static final String SEPARATOR = "---";
    private static final int MAX_LINES = 9999;

    private BookParser() {
    }

    public static Book parse(String bookId, String source) {
        List<String> raw = source.lines().toList();
        int separatorAt = raw.indexOf(SEPARATOR);
        if (separatorAt < 0) {
            throw new BookFormatException("В книге нет строки-разделителя ---", raw.size());
        }

        String title = title(raw.subList(0, separatorAt));
        List<Line> lines = new ArrayList<>();
        Map<String, Speaker> speakers = new LinkedHashMap<>();

        for (int i = separatorAt + 1; i < raw.size(); i++) {
            String text = raw.get(i);
            if (text.isBlank()) {
                continue;
            }
            int colon = text.indexOf(':');
            if (colon <= 0) {
                throw new BookFormatException(
                        "Реплика должна быть в виде «Имя: текст»", i + 1);
            }
            Speaker speaker = Speaker.of(text.substring(0, colon));
            speakers.putIfAbsent(speaker.id(), speaker);
            lines.add(new Line(lines.size() + 1, speaker.id(), text.substring(colon + 1).trim()));
            if (lines.size() > MAX_LINES) {
                throw new BookFormatException("В книге больше " + MAX_LINES + " реплик", i + 1);
            }
        }

        if (lines.isEmpty()) {
            throw new BookFormatException("В книге нет ни одной реплики", raw.size());
        }
        return new Book(bookId, title, lines, speakers);
    }

    private static String title(List<String> header) {
        for (int i = 0; i < header.size(); i++) {
            String line = header.get(i);
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new BookFormatException("Заголовок должен быть в виде «ключ: значение»", i + 1);
            }
            if (line.substring(0, colon).trim().equals("title")) {
                String title = line.substring(colon + 1).trim();
                if (title.isEmpty()) {
                    throw new BookFormatException("Пустой title у книги", i + 1);
                }
                return title;
            }
        }
        throw new BookFormatException("В заголовке книги нет поля title", 1);
    }
}
