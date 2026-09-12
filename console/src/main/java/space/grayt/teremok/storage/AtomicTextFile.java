package space.grayt.teremok.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Чтение и запись текстовых файлов хранилища в UTF-8. Запись атомарна. */
public final class AtomicTextFile {

    private static final String TEMP_SUFFIX = ".tmp";

    private AtomicTextFile() {
    }

    public static List<String> readLines(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, UTF_8);
        } catch (IOException e) {
            throw new StorageException("Не удалось прочитать файл " + file, e);
        }
    }

    public static void writeLines(Path file, List<String> lines) {
        Path temp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(temp, lines, UTF_8);
            move(temp, file);
        } catch (IOException e) {
            throw new StorageException("Не удалось записать файл " + file, e);
        }
    }

    public static Map<String, String> readProperties(Path file) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : readLines(file)) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue; // Битую строку пропускаем: один испорченный ключ не должен ронять файл.
            }
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return values;
    }

    public static void writeProperties(Path file, Map<String, String> values) {
        List<String> lines = new ArrayList<>(values.size());
        values.forEach((key, value) -> lines.add(key + "=" + value));
        writeLines(file, lines);
    }

    public static void deleteStaleTemp(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir, "*" + TEMP_SUFFIX)) {
            for (Path entry : entries) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException e) {
            throw new StorageException("Не удалось очистить временные файлы в " + dir, e);
        }
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, REPLACE_EXISTING);
        }
    }
}
