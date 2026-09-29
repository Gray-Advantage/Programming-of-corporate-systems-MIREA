package space.grayt.teremok.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import space.grayt.teremok.audio.AudioRecorder;
import space.grayt.teremok.audio.AudioUnavailableException;
import space.grayt.teremok.audio.RecordingSession;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.exception.BusinessRuleException;
import space.grayt.teremok.exception.EntityNotFoundException;
import space.grayt.teremok.storage.AudioStorage;
import space.grayt.teremok.storage.VoicingRepository;
import space.grayt.teremok.textwork.TextWorkCatalog;

/**
 * Voicing lifecycle: draft, recording fragments, publishing, taking down, deleting. The status
 * rules live here: DRAFT → PUBLISHED only when every fragment is recorded, PUBLISHED ⇄ ARCHIVED,
 * never back to DRAFT, and a published voicing cannot be deleted.
 */
public final class VoicingService {

    /** The recorder writes 16 kHz, 16-bit mono WAV: a 44-byte header and 32 bytes per millisecond. */
    private static final long WAV_HEADER_BYTES = 44;
    private static final long BYTES_PER_MILLISECOND = 32;
    /** The schema caps a fragment at two minutes, the recorder's own limit. */
    private static final long MAX_DURATION_MS = 120_000;

    private final VoicingRepository repository;
    private final TextWorkCatalog textWorks;
    private final AudioStorage audio;
    private final AudioRecorder recorder;
    private final Clock clock;

    public VoicingService(VoicingRepository repository, TextWorkCatalog textWorks, AudioStorage audio,
                          AudioRecorder recorder, Clock clock) {
        this.repository = repository;
        this.textWorks = textWorks;
        this.audio = audio;
        this.recorder = recorder;
        this.clock = clock;
    }

    /** The author's voicing of the voice part; a new draft the first time. */
    public Voicing draftFor(TextWork textWork, String voicePartId, String authorId) {
        if (textWork.voicePart(voicePartId).isEmpty()) {
            throw new EntityNotFoundException("В произведении «" + textWork.title() + "» нет такой роли.");
        }
        return repository.findByVoicePartAndAuthor(voicePartId, authorId)
                .orElseGet(() -> repository.insert(voicePartId, authorId, clock.instant()));
    }

    public Voicing reload(Voicing voicing) {
        return repository.find(voicing.id()).orElseThrow(VoicingService::notFound);
    }

    /** Voicing progress without creating it: zero for a voice part nobody has started. */
    public int recordedCountFor(TextWork textWork, String voicePartId, String authorId) {
        return repository.findByVoicePartAndAuthor(voicePartId, authorId)
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

    /** Drafts with every fragment recorded: «Опубликовать все готовые» publishes exactly these. */
    public List<Voicing> readyToPublish(List<Voicing> voicings) {
        return voicings.stream()
                .filter(voicing -> voicing.status() == VoicingStatus.DRAFT)
                .filter(voicing -> textWorks.find(voicing.textWorkId())
                        .map(textWork -> isComplete(voicing, textWork))
                        .orElse(false))
                .toList();
    }

    /** The recorded file of a fragment, if it has one. */
    public Optional<Path> audioFile(Voicing voicing, int fragmentNumber) {
        return Optional.ofNullable(repository.audioPaths(voicing.id()).get(fragmentNumber))
                .flatMap(audio::resolve);
    }

    /** The fragment counts as recorded once stop() has put the file in place. */
    public RecordingSession startRecording(Voicing voicing, int fragmentNumber) {
        audio.prepare(voicing.id());
        Path target = audio.fileFor(voicing.id(), fragmentNumber);
        return new SavingSession(recorder.start(target), voicing.id(), fragmentNumber, target);
    }

    /** DRAFT → PUBLISHED, or ARCHIVED → PUBLISHED again; both need every fragment recorded. */
    public Voicing publish(Voicing voicing, TextWork textWork) {
        Voicing current = reload(voicing);
        requireTransition(current, VoicingStatus.PUBLISHED);
        requireComplete(current, textWork);
        repository.updateStatus(current.id(), VoicingStatus.PUBLISHED, clock.instant());
        return current.withStatus(VoicingStatus.PUBLISHED);
    }

    /** PUBLISHED → ARCHIVED: listeners no longer get it, the votes stay. */
    public Voicing archive(Voicing voicing) {
        Voicing current = reload(voicing);
        requireTransition(current, VoicingStatus.ARCHIVED);
        repository.updateStatus(current.id(), VoicingStatus.ARCHIVED, clock.instant());
        return current.withStatus(VoicingStatus.ARCHIVED);
    }

    /**
     * Publishes all the given drafts or none of them. Each is checked here first, for a clear message;
     * the repository checks again inside its transaction.
     */
    public List<Voicing> publishAll(List<Voicing> drafts) {
        for (Voicing draft : drafts) {
            if (draft.status() != VoicingStatus.DRAFT) {
                throw new BusinessRuleException("Опубликовать все готовые можно только черновики.");
            }
            requireComplete(draft, textWorks.find(draft.textWorkId())
                    .orElseThrow(() -> new EntityNotFoundException("Произведение роли больше не доступно.")));
        }
        repository.publishAll(drafts.stream().map(Voicing::id).toList(), clock.instant());
        return drafts.stream().map(draft -> draft.withStatus(VoicingStatus.PUBLISHED)).toList();
    }

    public boolean canDelete(Voicing voicing) {
        return voicing.status() != VoicingStatus.PUBLISHED;
    }

    /** Deletes a draft or archived voicing with its recordings, votes and audio folder. */
    public void delete(Voicing voicing) {
        Voicing current = reload(voicing);
        if (!canDelete(current)) {
            throw new BusinessRuleException("Сначала снимите роль с публикации.");
        }
        repository.delete(current.id());
        audio.deleteVoicing(current.id());
    }

    public List<Voicing> byAuthor(String authorId) {
        return repository.findByAuthor(authorId);
    }

    private void requireComplete(Voicing voicing, TextWork textWork) {
        if (!isComplete(voicing, textWork)) {
            throw new BusinessRuleException("Опубликовать можно только полностью записанную роль: записано "
                    + recordedCount(voicing, textWork) + " из "
                    + textWork.fragmentsOf(voicing.voicePartId()).size());
        }
    }

    private static void requireTransition(Voicing voicing, VoicingStatus target) {
        if (voicing.status().canMoveTo(target)) {
            return;
        }
        throw new BusinessRuleException(switch (target) {
            case PUBLISHED -> "Роль уже опубликована.";
            case ARCHIVED -> voicing.status() == VoicingStatus.DRAFT
                    ? "Черновик ещё не опубликован, снимать с публикации нечего."
                    : "Роль уже снята с публикации.";
            case DRAFT -> "Вернуть роль в черновики нельзя.";
        });
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException("Озвучка не найдена: возможно, её уже удалили.");
    }

    /** Duration from the size of the WAV file, capped the same way as in the schema. */
    static int durationMs(long fileSize) {
        long millis = (fileSize - WAV_HEADER_BYTES) / BYTES_PER_MILLISECOND;
        return (int) Math.max(0, Math.min(MAX_DURATION_MS, millis));
    }

    /**
     * Writes the recording row after the recorder has moved the file into place, so the database never
     * points at audio that does not exist. The recorder may already have stopped itself at the two-minute
     * limit; stop() then only saves the row.
     */
    private final class SavingSession implements RecordingSession {

        private final RecordingSession recording;
        private final long voicingId;
        private final int fragmentNumber;
        private final Path target;
        private boolean saved;

        private SavingSession(RecordingSession recording, long voicingId, int fragmentNumber, Path target) {
            this.recording = recording;
            this.voicingId = voicingId;
            this.fragmentNumber = fragmentNumber;
            this.target = target;
        }

        @Override
        public boolean isRecording() {
            return recording.isRecording();
        }

        @Override
        public void stop() {
            recording.stop();
            if (saved) {
                return;
            }
            long size;
            try {
                size = Files.size(target);
            } catch (IOException e) {
                throw new AudioUnavailableException("Запись не сохранилась: нет файла " + target.getFileName(), e);
            }
            if (size == 0) {
                throw new AudioUnavailableException("Запись пустая, попробуйте ещё раз.");
            }
            repository.markRecorded(voicingId, fragmentNumber,
                    AudioStorage.relativePath(voicingId, fragmentNumber), durationMs(size));
            saved = true;
        }
    }
}
