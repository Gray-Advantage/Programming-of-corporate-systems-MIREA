package space.grayt.teremok.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.textwork.TextWorkParser;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.VoicePart;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.storage.FileVoicingRepository;
import space.grayt.teremok.storage.VoicingRepository;

class CastBuilderTest {

    private static final TextWork BOOK = TextWorkParser.parse("shapochka", """
            title: Красная Шапочка
            ---
            Волк: Куда ты идёшь?
            Шапочка: К бабушке.
            """);

    private VoicingRepository repository;
    private CastBuilder builder;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repository = new FileVoicingRepository(dir);
        builder = new CastBuilder(new VotingService(repository));
    }

    private Voicing publish(String speakerId, String author, int likes) {
        Voicing voicing = Voicing.newDraft("shapochka", speakerId, author, Instant.parse("2026-09-01T10:00:00Z"))
                .withStatus(VoicingStatus.PUBLISHED);
        repository.save(voicing);
        for (int i = 0; i < likes; i++) {
            repository.putVote(voicing.id(), "fan" + i, VoteKind.LIKE);
        }
        return voicing;
    }

    @Test
    void eachSpeakerGetsTheBestVoicing() {
        publish(VoicePart.idOf("Волк"), "weak", 1);
        Voicing top = publish(VoicePart.idOf("Волк"), "strong", 9);
        Voicing hood = publish(VoicePart.idOf("Шапочка"), "masha", 0);

        Cast cast = builder.best(BOOK);

        assertEquals(top.id(), cast.voicingFor(VoicePart.idOf("Волк")).orElseThrow());
        assertEquals(hood.id(), cast.voicingFor(VoicePart.idOf("Шапочка")).orElseThrow());
    }

    @Test
    void speakerWithoutVoicingsStaysUnvoiced() {
        publish(VoicePart.idOf("Волк"), "sergey", 1);

        Cast cast = builder.best(BOOK);

        assertTrue(cast.voicingFor(VoicePart.idOf("Шапочка")).isEmpty());
    }

    @Test
    void draftsAreNotCast() {
        repository.save(Voicing.newDraft("shapochka", VoicePart.idOf("Волк"), "sergey",
                Instant.parse("2026-09-01T10:00:00Z")));

        assertTrue(builder.best(BOOK).voicingFor(VoicePart.idOf("Волк")).isEmpty());
    }

    @Test
    void manualReplaceAndResetOfVoice() {
        Cast cast = Cast.empty().with("волк", "shapochka__волк__sergey");

        assertEquals("shapochka__волк__sergey", cast.voicingFor("волк").orElseThrow());
        assertTrue(cast.without("волк").voicingFor("волк").isEmpty());
    }
}
