package space.grayt.teremok.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import space.grayt.teremok.audio.AudioRecorder;
import space.grayt.teremok.audio.AudioUnavailableException;
import space.grayt.teremok.audio.RecordingSession;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Line;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.storage.AtomicTextFile;
import space.grayt.teremok.storage.VoicingRepository;

/** Жизненный цикл роли: черновик, запись реплик, публикация, удаление. */
public final class VoicingService {

    private final VoicingRepository repository;
    private final AudioRecorder recorder;
    private final Clock clock;

    public VoicingService(VoicingRepository repository, AudioRecorder recorder, Clock clock) {
        this.repository = repository;
        this.recorder = recorder;
        this.clock = clock;
    }

    public Voicing draftFor(Book book, String speakerId, String authorId) {
        String id = Voicing.idOf(book.id(), speakerId, authorId);
        return repository.find(id).orElseGet(() -> {
            Voicing draft = Voicing.newDraft(book.id(), speakerId, authorId, clock.instant());
            repository.save(draft);
            return draft;
        });
    }

    public Voicing reload(Voicing voicing) {
        return repository.find(voicing.id()).orElse(voicing);
    }

    /** Прогресс роли без её создания: для персонажа, которого ещё не начинали, — ноль. */
    public int recordedCountFor(Book book, String speakerId, String authorId) {
        return repository.find(Voicing.idOf(book.id(), speakerId, authorId))
                .map(voicing -> recordedCount(voicing, book))
                .orElse(0);
    }

    public List<Line> missingLines(Voicing voicing, Book book) {
        return book.linesOf(voicing.speakerId()).stream()
                .filter(line -> !voicing.isRecorded(line.number()))
                .toList();
    }

    public int recordedCount(Voicing voicing, Book book) {
        return book.linesOf(voicing.speakerId()).size() - missingLines(voicing, book).size();
    }

    public boolean isComplete(Voicing voicing, Book book) {
        return !book.linesOf(voicing.speakerId()).isEmpty() && missingLines(voicing, book).isEmpty();
    }

    public java.nio.file.Path audioFile(Voicing voicing, int lineNumber) {
        return repository.audioFile(voicing.id(), lineNumber);
    }

    public RecordingSession startRecording(Voicing voicing, int lineNumber) {
        Path target = repository.audioFile(voicing.id(), lineNumber);
        Path dir = target.getParent();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new AudioUnavailableException("Не удалось подготовить папку для записи " + dir, e);
        }
        AtomicTextFile.deleteStaleTemp(dir);
        return recorder.start(target);
    }

    public Voicing publish(Voicing voicing, Book book) {
        if (!isComplete(voicing, book)) {
            throw new IllegalStateException("Опубликовать можно только полностью записанную роль: записано "
                    + recordedCount(voicing, book) + " из " + book.linesOf(voicing.speakerId()).size());
        }
        Voicing published = voicing.withStatus(VoicingStatus.PUBLISHED);
        repository.save(published);
        return published;
    }

    public Voicing unpublish(Voicing voicing) {
        Voicing draft = voicing.withStatus(VoicingStatus.DRAFT);
        repository.save(draft);
        return draft;
    }

    public void delete(Voicing voicing) {
        repository.delete(voicing.id());
    }
}
