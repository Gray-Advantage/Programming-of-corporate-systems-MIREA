package space.grayt.teremok.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;

/** Каждая роль — отдельная папка data/voicings/<id> с meta.txt, votes.txt и файлами реплик. */
public final class FileVoicingRepository implements VoicingRepository {

    private static final String META = "meta.txt";
    private static final String VOTES = "votes.txt";
    private static final String AUDIO_PREFIX = "line-";
    private static final String AUDIO_SUFFIX = ".wav";

    private final Path root;
    private final List<String> warnings = new ArrayList<>();

    public FileVoicingRepository(Path dataDir) {
        this.root = dataDir.resolve("voicings");
    }

    @Override
    public Optional<Voicing> find(String voicingId) {
        Path dir = root.resolve(voicingId);
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        Map<String, String> meta = AtomicTextFile.readProperties(dir.resolve(META));
        try {
            Voicing voicing = new Voicing(
                    voicingId,
                    required(meta, "book", voicingId),
                    required(meta, "speaker", voicingId),
                    required(meta, "author", voicingId),
                    VoicingStatus.valueOf(required(meta, "status", voicingId)),
                    Instant.parse(required(meta, "created", voicingId)),
                    recordedLines(dir));
            return Optional.of(voicing);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            warnings.add("Роль " + voicingId + " пропущена: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Voicing> findByBook(String bookId) {
        return all().filter(voicing -> voicing.bookId().equals(bookId)).toList();
    }

    @Override
    public List<Voicing> findByAuthor(String authorId) {
        return all().filter(voicing -> voicing.authorId().equals(authorId)).toList();
    }

    @Override
    public void save(Voicing voicing) {
        AtomicTextFile.writeProperties(root.resolve(voicing.id()).resolve(META), Map.of(
                "book", voicing.bookId(),
                "speaker", voicing.speakerId(),
                "author", voicing.authorId(),
                "status", voicing.status().name(),
                "created", voicing.createdAt().toString()));
    }

    @Override
    public void delete(String voicingId) {
        Path dir = root.resolve(voicingId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(dir)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new StorageException("Не удалось удалить озвучку " + voicingId, e);
        }
    }

    @Override
    public Path audioFile(String voicingId, int lineNumber) {
        return root.resolve(voicingId).resolve(String.format(AUDIO_PREFIX + "%04d" + AUDIO_SUFFIX, lineNumber));
    }

    @Override
    public List<Vote> votes(String voicingId) {
        List<Vote> result = new ArrayList<>();
        AtomicTextFile.readProperties(root.resolve(voicingId).resolve(VOTES)).forEach((profileId, kind) -> {
            try {
                result.add(new Vote(profileId, VoteKind.valueOf(kind)));
            } catch (IllegalArgumentException e) {
                warnings.add("Непонятный голос «" + kind + "» в озвучке " + voicingId);
            }
        });
        return result;
    }

    @Override
    public void putVote(String voicingId, String profileId, VoteKind kind) {
        Path file = root.resolve(voicingId).resolve(VOTES);
        Map<String, String> all = AtomicTextFile.readProperties(file);
        all.put(profileId, kind.name());
        AtomicTextFile.writeProperties(file, all);
    }

    @Override
    public void removeVote(String voicingId, String profileId) {
        Path file = root.resolve(voicingId).resolve(VOTES);
        Map<String, String> all = AtomicTextFile.readProperties(file);
        all.remove(profileId);
        AtomicTextFile.writeProperties(file, all);
    }

    @Override
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private Stream<Voicing> all() {
        if (!Files.isDirectory(root)) {
            return Stream.of();
        }
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .map(dir -> dir.getFileName().toString())
                    .sorted()
                    .map(this::find)
                    .flatMap(Optional::stream)
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new StorageException("Не удалось прочитать каталог озвучек " + root, e);
        }
    }

    private static String required(Map<String, String> meta, String key, String voicingId) {
        String value = meta.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("в meta.txt нет поля " + key);
        }
        return value;
    }

    /** Реплика считается записанной, только если файл существует и непустой. */
    private static Set<Integer> recordedLines(Path dir) {
        Set<Integer> numbers = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.startsWith(AUDIO_PREFIX) || !name.endsWith(AUDIO_SUFFIX)) {
                    continue;
                }
                if (Files.size(file) == 0) {
                    continue;
                }
                String digits = name.substring(AUDIO_PREFIX.length(), name.length() - AUDIO_SUFFIX.length());
                try {
                    numbers.add(Integer.parseInt(digits));
                } catch (NumberFormatException e) {
                    // Посторонний файл в папке роли просто игнорируем.
                }
            }
        } catch (IOException e) {
            throw new StorageException("Не удалось прочитать файлы озвучки в " + dir, e);
        }
        return numbers;
    }
}
