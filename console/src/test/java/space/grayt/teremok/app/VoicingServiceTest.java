package space.grayt.teremok.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.audio.FakeAudioRecorder;
import space.grayt.teremok.audio.RecordingSession;
import space.grayt.teremok.book.BookParser;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Line;
import space.grayt.teremok.domain.Speaker;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;

class VoicingServiceTest {

    private static final Book BOOK = BookParser.parse("shapochka", """
            title: Красная Шапочка
            ---
            Волк: Куда ты идёшь?
            Шапочка: К бабушке.
            Волк: А где живёт бабушка?
            """);
    private static final String ВОЛК = Speaker.idOf("Волк");
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

    private VoicingRepository repository;
    private FakeAudioRecorder recorder;
    private VoicingService service;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repository = new FileVoicingRepository(dir);
        recorder = new FakeAudioRecorder();
        service = new VoicingService(repository, recorder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void record(Voicing voicing, int lineNumber) {
        RecordingSession session = service.startRecording(voicing, lineNumber);
        session.stop();
    }

    @Test
    void черновикСоздаётсяОдинРазИПереиспользуется() {
        Voicing first = service.draftFor(BOOK, ВОЛК, "sergey");
        Voicing second = service.draftFor(BOOK, ВОЛК, "sergey");

        assertEquals(first.id(), second.id());
        assertEquals(VoicingStatus.DRAFT, second.status());
        assertEquals(NOW, second.createdAt());
        assertEquals(1, repository.findByAuthor("sergey").size());
    }

    @Test
    void незаписанныеРепликиЭтоРепликиПерсонажа() {
        Voicing voicing = service.draftFor(BOOK, ВОЛК, "sergey");

        assertEquals(List.of(1, 3), service.missingLines(voicing, BOOK).stream().map(Line::number).toList());
    }

    @Test
    void записьРепликиСоздаётФайлИУменьшаетОстаток() {
        Voicing voicing = service.draftFor(BOOK, ВОЛК, "sergey");

        record(voicing, 1);
        Voicing reloaded = service.reload(voicing);

        assertEquals(1, service.recordedCount(reloaded, BOOK));
        assertEquals(List.of(3), service.missingLines(reloaded, BOOK).stream().map(Line::number).toList());
        assertTrue(Files.exists(repository.audioFile(voicing.id(), 1)));
    }

    @Test
    void рольПолнаяТолькоКогдаЗаписаныВсеЕёРеплики() {
        Voicing voicing = service.draftFor(BOOK, ВОЛК, "sergey");
        record(voicing, 1);

        assertFalse(service.isComplete(service.reload(voicing), BOOK));

        record(voicing, 3);

        assertTrue(service.isComplete(service.reload(voicing), BOOK));
    }

    @Test
    void неполнуюРольОпубликоватьНельзя() {
        Voicing voicing = service.draftFor(BOOK, ВОЛК, "sergey");
        record(voicing, 1);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.publish(service.reload(voicing), BOOK));

        assertTrue(error.getMessage().contains("1 из 2"));
    }

    @Test
    void полнаяРольПубликуетсяИСнимается() {
        Voicing voicing = service.draftFor(BOOK, ВОЛК, "sergey");
        record(voicing, 1);
        record(voicing, 3);

        Voicing published = service.publish(service.reload(voicing), BOOK);
        assertEquals(VoicingStatus.PUBLISHED, published.status());
        assertEquals(VoicingStatus.PUBLISHED, repository.find(voicing.id()).orElseThrow().status());

        Voicing hidden = service.unpublish(published);
        assertEquals(VoicingStatus.DRAFT, hidden.status());
        assertEquals(VoicingStatus.DRAFT, repository.find(voicing.id()).orElseThrow().status());
    }

    @Test
    void пропавшийФайлДелаетОпубликованнуюРольНеполной() throws Exception {
        Voicing voicing = service.draftFor(BOOK, ВОЛК, "sergey");
        record(voicing, 1);
        record(voicing, 3);
        service.publish(service.reload(voicing), BOOK);

        Files.delete(repository.audioFile(voicing.id(), 3));

        assertFalse(service.isComplete(service.reload(voicing), BOOK));
    }

    @Test
    void удалениеРолиУноситВсеЕёФайлы() {
        Voicing voicing = service.draftFor(BOOK, ВОЛК, "sergey");
        record(voicing, 1);

        service.delete(voicing);

        assertTrue(repository.find(voicing.id()).isEmpty());
    }

    @Test
    void передЗаписьюУдаляютсяОбрывкиПрошлыхПопыток() throws Exception {
        Voicing voicing = service.draftFor(BOOK, ВОЛК, "sergey");
        Path dir = repository.audioFile(voicing.id(), 1).getParent();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("line-0003.wav.tmp"), "обрывок");

        record(voicing, 1);

        assertFalse(Files.exists(dir.resolve("line-0003.wav.tmp")));
    }
}
