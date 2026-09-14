package space.grayt.teremok.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicTextFileTest {

    @Test
    void writesAndReadsCyrillic(@TempDir Path dir) {
        Path file = dir.resolve("вложенная/книга.txt");

        AtomicTextFile.writeLines(file, List.of("Волк: Куда ты идёшь?", "Шапочка: К бабушке."));

        assertEquals(List.of("Волк: Куда ты идёшь?", "Шапочка: К бабушке."),
                AtomicTextFile.readLines(file));
    }

    @Test
    void missingFileReadsAsEmpty(@TempDir Path dir) {
        assertEquals(List.of(), AtomicTextFile.readLines(dir.resolve("нет.txt")));
    }

    @Test
    void noTempFileRemainsAfterWrite(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("meta.txt");

        AtomicTextFile.writeLines(file, List.of("book=shapochka"));

        try (var entries = Files.list(dir)) {
            assertEquals(List.of("meta.txt"), entries.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void rewriteReplacesContentCompletely(@TempDir Path dir) {
        Path file = dir.resolve("votes.txt");

        AtomicTextFile.writeLines(file, List.of("masha=LIKE", "petya=DISLIKE"));
        AtomicTextFile.writeLines(file, List.of("masha=LIKE"));

        assertEquals(List.of("masha=LIKE"), AtomicTextFile.readLines(file));
    }

    @Test
    void propertiesAreReadAsPairsAndMalformedLinesSkipped(@TempDir Path dir) {
        Path file = dir.resolve("meta.txt");
        AtomicTextFile.writeLines(file, List.of("book=shapochka", "мусор без равно", "=пустой ключ", "author=sergey"));

        Map<String, String> values = AtomicTextFile.readProperties(file);

        assertEquals(Map.of("book", "shapochka", "author", "sergey"), values);
    }

    @Test
    void propertiesAreWrittenAndReadInSameOrder(@TempDir Path dir) {
        Path file = dir.resolve("meta.txt");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("book", "shapochka");
        values.put("speaker", "волк");

        AtomicTextFile.writeProperties(file, values);

        assertEquals(List.of("book=shapochka", "speaker=волк"), AtomicTextFile.readLines(file));
    }

    @Test
    void staleTempFilesAreDeleted(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("line-0001.wav.tmp"), "обрывок");
        Files.writeString(dir.resolve("line-0002.wav"), "целый");

        AtomicTextFile.deleteStaleTemp(dir);

        assertFalse(Files.exists(dir.resolve("line-0001.wav.tmp")));
        assertTrue(Files.exists(dir.resolve("line-0002.wav")));
    }
}
