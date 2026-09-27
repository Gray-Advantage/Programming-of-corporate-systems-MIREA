package space.grayt.teremok.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import space.grayt.teremok.audio.AudioRecorder;
import space.grayt.teremok.audio.AudioUnavailableException;
import space.grayt.teremok.audio.RecordingSession;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.storage.AtomicTextFile;
import space.grayt.teremok.storage.VoicingRepository;

/** Voicing lifecycle: draft, recording fragments, publishing, deleting. */
public final class VoicingService {

    private final VoicingRepository repository;
    private final AudioRecorder recorder;
    private final Clock clock;

    public VoicingService(VoicingRepository repository, AudioRecorder recorder, Clock clock) {
        this.repository = repository;
        this.recorder = recorder;
        this.clock = clock;
    }

    public Voicing draftFor(TextWork textWork, String voicePartId, String authorId) {
        String id = Voicing.idOf(textWork.id(), voicePartId, authorId);
        return repository.find(id).orElseGet(() -> {
            Voicing draft = Voicing.newDraft(textWork.id(), voicePartId, authorId, clock.instant());
            repository.save(draft);
            return draft;
        });
    }

    public Voicing reload(Voicing voicing) {
        return repository.find(voicing.id()).orElse(voicing);
    }

    /** Voicing progress without creating it: zero for a voice part nobody has started. */
    public int recordedCountFor(TextWork textWork, String voicePartId, String authorId) {
        return repository.find(Voicing.idOf(textWork.id(), voicePartId, authorId))
                .map(voicing -> recordedCount(voicing, textWork))
                .orElse(0);
    }

    public List<TextWorkFragment> missingFragments(Voicing voicing, TextWork textWork) {
        return textWork.fragmentsOf(voicing.voicePartId()).stream()
                .filter(fragment -> !voicing.isRecorded(fragment.number()))
                .toList();
    }

    public int recordedCount(Voicing voicing, TextWork textWork) {
        return textWork.fragmentsOf(voicing.voicePartId()).size() - missingFragments(voicing, textWork).size();
    }

    public boolean isComplete(Voicing voicing, TextWork textWork) {
        return !textWork.fragmentsOf(voicing.voicePartId()).isEmpty()
                && missingFragments(voicing, textWork).isEmpty();
    }

    public Path audioFile(Voicing voicing, int fragmentNumber) {
        return repository.audioFile(voicing.id(), fragmentNumber);
    }

    public RecordingSession startRecording(Voicing voicing, int fragmentNumber) {
        Path target = repository.audioFile(voicing.id(), fragmentNumber);
        Path dir = target.getParent();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new AudioUnavailableException("Не удалось подготовить папку для записи " + dir, e);
        }
        AtomicTextFile.deleteStaleTemp(dir);
        return recorder.start(target);
    }

    public Voicing publish(Voicing voicing, TextWork textWork) {
        if (!isComplete(voicing, textWork)) {
            throw new IllegalStateException("Опубликовать можно только полностью записанную роль: записано "
                    + recordedCount(voicing, textWork) + " из "
                    + textWork.fragmentsOf(voicing.voicePartId()).size());
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

    public List<Voicing> byAuthor(String authorId) {
        return repository.findByAuthor(authorId);
    }
}
