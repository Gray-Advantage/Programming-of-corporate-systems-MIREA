package space.grayt.teremok.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;

class FileVoicingRepositoryTest {

    private static final Instant CREATED = Instant.parse("2026-09-07T12:00:00Z");

    private Voicing draft(String author) {
        return Voicing.newDraft("shapochka", "волк", author, CREATED);
    }

    private void writeAudio(VoicingRepository repository, Voicing voicing, int line, String content)
            throws Exception {
        Path file = repository.audioFile(voicing.id(), line);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void идентификаторСобираетсяИзКнигиПерсонажаИАвтора() {
        assertEquals("shapochka__волк__sergey", Voicing.idOf("shapochka", "волк", "sergey"));
    }

    @Test
    void сохраняетИЧитаетРоль(@TempDir Path dir) {
        VoicingRepository repository = new FileVoicingRepository(dir);
        Voicing voicing = draft("sergey");

        repository.save(voicing);
        Voicing loaded = repository.find(voicing.id()).orElseThrow();

        assertEquals("shapochka", loaded.bookId());
        assertEquals("волк", loaded.speakerId());
        assertEquals("sergey", loaded.authorId());
        assertEquals(VoicingStatus.DRAFT, loaded.status());
        assertEquals(CREATED, loaded.createdAt());
    }

    @Test
    void записанныеРепликиВыводятсяИзНепустыхФайлов(@TempDir Path dir) throws Exception {
        VoicingRepository repository = new FileVoicingRepository(dir);
        Voicing voicing = draft("sergey");
        repository.save(voicing);
        writeAudio(repository, voicing, 2, "звук");
        writeAudio(repository, voicing, 7, "звук");
        writeAudio(repository, voicing, 9, "");

        Voicing loaded = repository.find(voicing.id()).orElseThrow();

        assertEquals(Set.of(2, 7), loaded.recordedLines());
    }

    @Test
    void имяФайлаАудиоСЧетырьмяЗнаками(@TempDir Path dir) {
        VoicingRepository repository = new FileVoicingRepository(dir);

        assertEquals("line-0007.wav",
                repository.audioFile("shapochka__волк__sergey", 7).getFileName().toString());
    }

    @Test
    void сменаСтатусаСохраняется(@TempDir Path dir) {
        VoicingRepository repository = new FileVoicingRepository(dir);
        Voicing voicing = draft("sergey");
        repository.save(voicing);

        repository.save(voicing.withStatus(VoicingStatus.PUBLISHED));

        assertEquals(VoicingStatus.PUBLISHED, repository.find(voicing.id()).orElseThrow().status());
    }

    @Test
    void ищетПоКнигеИПоАвтору(@TempDir Path dir) {
        VoicingRepository repository = new FileVoicingRepository(dir);
        repository.save(draft("sergey"));
        repository.save(Voicing.newDraft("shapochka", "бабушка", "masha", CREATED));
        repository.save(Voicing.newDraft("teremok", "мышка", "masha", CREATED));

        assertEquals(2, repository.findByBook("shapochka").size());
        assertEquals(2, repository.findByAuthor("masha").size());
    }

    @Test
    void битаяРольПропускаетсяИПопадаетВПредупреждения(@TempDir Path dir) throws Exception {
        VoicingRepository repository = new FileVoicingRepository(dir);
        repository.save(draft("sergey"));
        Path broken = dir.resolve("voicings").resolve("мусор");
        Files.createDirectories(broken);
        Files.writeString(broken.resolve("meta.txt"), "совсем не то");

        List<Voicing> found = repository.findByBook("shapochka");

        assertEquals(1, found.size());
        assertFalse(repository.warnings().isEmpty());
    }

    @Test
    void повторныйВызовНеДублируетПредупреждениеОБитойРоли(@TempDir Path dir) throws Exception {
        VoicingRepository repository = new FileVoicingRepository(dir);
        repository.save(draft("sergey"));
        Path broken = dir.resolve("voicings").resolve("мусор");
        Files.createDirectories(broken);
        Files.writeString(broken.resolve("meta.txt"), "совсем не то");

        repository.findByBook("shapochka");
        repository.findByBook("shapochka");

        assertEquals(1, repository.warnings().size());
    }

    @Test
    void удалениеУноситПапкуЦеликом(@TempDir Path dir) throws Exception {
        VoicingRepository repository = new FileVoicingRepository(dir);
        Voicing voicing = draft("sergey");
        repository.save(voicing);
        writeAudio(repository, voicing, 1, "звук");

        repository.delete(voicing.id());

        assertTrue(repository.find(voicing.id()).isEmpty());
        assertFalse(Files.exists(dir.resolve("voicings").resolve(voicing.id())));
    }

    @Test
    void голосаСохраняютсяЗаменяютсяИСнимаются(@TempDir Path dir) {
        VoicingRepository repository = new FileVoicingRepository(dir);
        Voicing voicing = draft("sergey");
        repository.save(voicing);

        repository.putVote(voicing.id(), "masha", VoteKind.LIKE);
        assertEquals(List.of(new Vote("masha", VoteKind.LIKE)), repository.votes(voicing.id()));

        repository.putVote(voicing.id(), "masha", VoteKind.DISLIKE);
        assertEquals(List.of(new Vote("masha", VoteKind.DISLIKE)), repository.votes(voicing.id()));

        repository.removeVote(voicing.id(), "masha");
        assertEquals(List.of(), repository.votes(voicing.id()));
    }

    @Test
    void битаяСтрокаГолосаПропускается(@TempDir Path dir) throws Exception {
        VoicingRepository repository = new FileVoicingRepository(dir);
        Voicing voicing = draft("sergey");
        repository.save(voicing);
        Path votes = dir.resolve("voicings").resolve(voicing.id()).resolve("votes.txt");
        Files.writeString(votes, "masha=LIKE\npetya=НЕПОНЯТНО\nkatya=DISLIKE\n");

        assertEquals(List.of(new Vote("masha", VoteKind.LIKE), new Vote("katya", VoteKind.DISLIKE)),
                repository.votes(voicing.id()));
    }
}
