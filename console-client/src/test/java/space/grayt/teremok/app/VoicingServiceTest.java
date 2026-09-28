package space.grayt.teremok.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.Fixture;
import space.grayt.teremok.audio.AudioRecorder;
import space.grayt.teremok.audio.AudioUnavailableException;
import space.grayt.teremok.audio.RecordingSession;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.exception.BusinessRuleException;
import space.grayt.teremok.exception.EntityNotFoundException;
import space.grayt.teremok.storage.VoicingRepository;

class VoicingServiceTest {

    private Fixture fixture;
    private TextWork book;
    private String wolf;
    private VoicingRepository repository;
    private VoicingService service;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        fixture = Fixture.empty(dir);
        book = fixture.addTextWork("Красная Шапочка",
                "Волк: Куда ты идёшь?",
                "Шапочка: К бабушке.",
                "Волк: А где живёт бабушка?");
        wolf = fixture.voicePartId(book, "Волк");
        fixture.profile("sergey");
        repository = fixture.voicingRepository;
        service = fixture.voicings();
    }

    private void record(Voicing voicing, int fragmentNumber) {
        RecordingSession session = service.startRecording(voicing, fragmentNumber);
        session.stop();
    }

    private Voicing completeWolf() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");
        record(voicing, 1);
        record(voicing, 3);
        return service.reload(voicing);
    }

    private static List<Integer> numbers(List<TextWorkFragment> fragments) {
        return fragments.stream().map(TextWorkFragment::number).toList();
    }

    @Test
    void draftIsCreatedOnceAndReused() {
        Voicing first = service.draftFor(book, wolf, "sergey");
        Voicing second = service.draftFor(book, wolf, "sergey");

        assertEquals(first.id(), second.id());
        assertEquals(VoicingStatus.DRAFT, second.status());
        assertEquals(Fixture.NOW, second.createdAt());
        assertEquals(book.id(), second.textWorkId());
        assertEquals(1, repository.findByAuthor("sergey").size());
    }

    @Test
    void voicePartOfAnotherTextWorkIsNotFound() {
        TextWork other = fixture.addTextWork("Репка", "Дед: Тянем-потянем.");

        assertThrows(EntityNotFoundException.class,
                () -> service.draftFor(book, fixture.voicePartId(other, "Дед"), "sergey"));
    }

    @Test
    void missingFragmentsAreTheVoicePartsFragments() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");

        assertEquals(List.of(1, 3), numbers(service.missingFragments(voicing, book)));
    }

    @Test
    void recordingFragmentCreatesFileAndRecordingRow() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");

        record(voicing, 1);
        Voicing reloaded = service.reload(voicing);

        assertEquals(1, service.recordedCount(reloaded, book));
        assertEquals(List.of(3), numbers(service.missingFragments(reloaded, book)));
        assertTrue(Files.exists(fixture.audio.fileFor(voicing.id(), 1)));
        assertEquals(Map.of(1, voicing.id() + "/fragment-0001.wav"), repository.audioPaths(voicing.id()));
        assertEquals(fixture.audio.fileFor(voicing.id(), 1), service.audioFile(reloaded, 1).orElseThrow());
    }

    @Test
    void rerecordingReplacesRecordingRow() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");

        record(voicing, 1);
        record(voicing, 1);

        assertEquals(1, repository.audioPaths(voicing.id()).size());
        assertEquals(2, fixture.recorder.recorded().size());
    }

    /** 16 kHz, 16-bit mono: 32 bytes per millisecond after the 44-byte header. */
    @Test
    void durationIsComputedFromFileSize() {
        assertEquals(1000, VoicingService.durationMs(44 + 32_000));
        assertEquals(0, VoicingService.durationMs(29));
        assertEquals(120_000, VoicingService.durationMs(44 + 32L * 130_000));
    }

    @Test
    void failedRecordingSavesNoRow() {
        AudioRecorder broken = new AudioRecorder() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public RecordingSession start(Path target) {
                return new RecordingSession() {
                    @Override
                    public void stop() {
                        throw new AudioUnavailableException("Не удалось сохранить запись");
                    }

                    @Override
                    public boolean isRecording() {
                        return true;
                    }
                };
            }
        };
        VoicingService withBrokenRecorder = new VoicingService(repository, fixture.textWorks, fixture.audio,
                broken, fixture.clock);
        Voicing voicing = withBrokenRecorder.draftFor(book, wolf, "sergey");

        RecordingSession session = withBrokenRecorder.startRecording(voicing, 1);

        assertThrows(AudioUnavailableException.class, session::stop);
        assertTrue(repository.audioPaths(voicing.id()).isEmpty());
    }

    @Test
    void secondStopDoesNotSaveAgain() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");
        RecordingSession session = service.startRecording(voicing, 1);

        session.stop();
        session.stop();

        assertEquals(1, repository.audioPaths(voicing.id()).size());
    }

    @Test
    void voicingIsCompleteOnlyWhenAllFragmentsRecorded() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");
        record(voicing, 1);

        assertFalse(service.isComplete(service.reload(voicing), book));

        record(voicing, 3);

        assertTrue(service.isComplete(service.reload(voicing), book));
    }

    @Test
    void incompleteVoicingCannotBePublished() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");
        record(voicing, 1);

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.publish(service.reload(voicing), book));

        assertTrue(error.getMessage().contains("1 из 2"), error::getMessage);
        assertEquals(VoicingStatus.DRAFT, service.reload(voicing).status());
    }

    @Test
    void completeVoicingIsPublishedArchivedAndPublishedAgain() {
        Voicing voicing = completeWolf();

        assertEquals(VoicingStatus.PUBLISHED, service.publish(voicing, book).status());
        assertEquals(VoicingStatus.PUBLISHED, service.reload(voicing).status());

        assertEquals(VoicingStatus.ARCHIVED, service.archive(voicing).status());
        assertEquals(VoicingStatus.ARCHIVED, service.reload(voicing).status());

        assertEquals(VoicingStatus.PUBLISHED, service.publish(voicing, book).status());
        assertEquals(VoicingStatus.PUBLISHED, service.reload(voicing).status());
    }

    @Test
    void archivingKeepsVotes() {
        Voicing voicing = service.publish(completeWolf(), book);
        fixture.vote(voicing, VoteKind.LIKE, "masha");

        service.archive(voicing);

        assertEquals(1, repository.votes(voicing.id()).size());
    }

    @Test
    void draftCannotBeArchived() {
        Voicing voicing = completeWolf();

        assertThrows(BusinessRuleException.class, () -> service.archive(voicing));
        assertEquals(VoicingStatus.DRAFT, service.reload(voicing).status());
    }

    @Test
    void publishedVoicingCannotBePublishedAgain() {
        Voicing voicing = service.publish(completeWolf(), book);

        assertThrows(BusinessRuleException.class, () -> service.publish(voicing, book));
    }

    /** The service decides by the stored status, not by the copy the screen holds. */
    @Test
    void staleCopyDoesNotBypassRules() {
        Voicing draftCopy = completeWolf();
        service.publish(draftCopy, book);

        assertThrows(BusinessRuleException.class, () -> service.delete(draftCopy));
        assertTrue(repository.find(draftCopy.id()).isPresent());
    }

    @Test
    void deletingDraftRemovesRowAndAudioFolder() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");
        record(voicing, 1);

        service.delete(voicing);

        assertTrue(repository.find(voicing.id()).isEmpty());
        assertFalse(Files.exists(fixture.audio.fileFor(voicing.id(), 1).getParent()));
    }

    @Test
    void archivedVoicingCanBeDeleted() {
        Voicing voicing = service.archive(service.publish(completeWolf(), book));

        service.delete(voicing);

        assertTrue(repository.find(voicing.id()).isEmpty());
    }

    @Test
    void publishedVoicingCannotBeDeleted() {
        Voicing voicing = service.publish(completeWolf(), book);

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> service.delete(voicing));

        assertEquals("Сначала снимите роль с публикации.", error.getMessage());
        assertTrue(repository.find(voicing.id()).isPresent());
        assertFalse(service.canDelete(voicing));
    }

    @Test
    void deletedVoicingIsNotFoundOnReload() {
        Voicing voicing = service.draftFor(book, wolf, "sergey");
        service.delete(voicing);

        assertThrows(EntityNotFoundException.class, () -> service.reload(voicing));
    }

    @Test
    void readyToPublishIsCompleteDraftsOnly() {
        Voicing ready = completeWolf();
        Voicing unfinished = fixture.draft(book, "Шапочка", "sergey");

        assertEquals(List.of(ready.id()),
                service.readyToPublish(List.of(ready, unfinished)).stream().map(Voicing::id).toList());
    }

    @Test
    void publishAllPublishesEveryReadyDraft() {
        Voicing wolfVoicing = completeWolf();
        Voicing hoodVoicing = fixture.recordAll(fixture.draft(book, "Шапочка", "sergey"), book);

        List<Voicing> published = service.publishAll(List.of(wolfVoicing, hoodVoicing));

        assertEquals(2, published.size());
        assertEquals(VoicingStatus.PUBLISHED, service.reload(wolfVoicing).status());
        assertEquals(VoicingStatus.PUBLISHED, service.reload(hoodVoicing).status());
    }

    @Test
    void publishAllPublishesNothingWhenOneDraftIsNotReady() {
        Voicing ready = completeWolf();
        Voicing unfinished = fixture.draft(book, "Шапочка", "sergey");

        assertThrows(BusinessRuleException.class, () -> service.publishAll(List.of(ready, unfinished)));

        assertEquals(VoicingStatus.DRAFT, service.reload(ready).status());
        assertEquals(VoicingStatus.DRAFT, service.reload(unfinished).status());
    }

    @Test
    void staleTempFilesAreRemovedBeforeRecording() throws Exception {
        Voicing voicing = service.draftFor(book, wolf, "sergey");
        Path dir = fixture.audio.fileFor(voicing.id(), 1).getParent();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("fragment-0003.wav.tmp"), "обрывок");

        record(voicing, 1);

        assertFalse(Files.exists(dir.resolve("fragment-0003.wav.tmp")));
    }
}
