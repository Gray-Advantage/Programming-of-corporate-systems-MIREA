package space.grayt.teremok.storage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * WAV files of recorded fragments, laid out as root/voicingId/fragment-NNNN.wav. The database keeps
 * the path relative to the root, so the audio directory can move without touching the rows.
 */
public final class AudioStorage {

    private static final String TEMP_SUFFIX = ".tmp";

    private final Path root;

    public AudioStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** Forward slashes on every system: the path is stored in the database, not only used locally. */
    public static String relativePath(long voicingId, int fragmentNumber) {
        return voicingId + "/" + String.format(Locale.ROOT, "fragment-%04d.wav", fragmentNumber);
    }

    public Path fileFor(long voicingId, int fragmentNumber) {
        return root.resolve(relativePath(voicingId, fragmentNumber));
    }

    /** Empty when the stored path would lead outside the audio directory. */
    public Optional<Path> resolve(String relativePath) {
        Path file = root.resolve(relativePath).normalize();
        return file.startsWith(root) ? Optional.of(file) : Optional.empty();
    }

    /** An empty or missing file counts as no recording. */
    public static boolean isPlayable(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Creates the voicing directory and removes temp files left by an interrupted recording. */
    public void prepare(long voicingId) {
        Path dir = directoryOf(voicingId);
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> temps = Files.newDirectoryStream(dir, "*" + TEMP_SUFFIX)) {
                for (Path temp : temps) {
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException e) {
            throw new StorageException("Не удалось подготовить папку для записи " + dir, e);
        }
    }

    public void deleteVoicing(long voicingId) {
        Path dir = directoryOf(voicingId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(dir)) {
            List<Path> deepestFirst = entries.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : deepestFirst) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException e) {
            throw new StorageException("Роль удалена, но папку с её записями удалить не удалось: " + dir, e);
        }
    }

    private Path directoryOf(long voicingId) {
        return root.resolve(Long.toString(voicingId));
    }
}
