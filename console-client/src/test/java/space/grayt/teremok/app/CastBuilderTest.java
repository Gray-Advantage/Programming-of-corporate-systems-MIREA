package space.grayt.teremok.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.Fixture;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;

class CastBuilderTest {

    private Fixture fixture;
    private TextWork book;
    private String wolf;
    private String hood;
    private CastBuilder builder;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        fixture = Fixture.empty(dir);
        book = fixture.addTextWork("Красная Шапочка", "Волк: Куда ты идёшь?", "Шапочка: К бабушке.");
        wolf = fixture.voicePartId(book, "Волк");
        hood = fixture.voicePartId(book, "Шапочка");
        builder = fixture.castBuilder();
    }

    private Voicing publish(String voicePartName, String author, int likes) {
        Voicing voicing = fixture.voicing(book, voicePartName, author, VoicingStatus.PUBLISHED);
        fixture.likes(voicing, likes);
        return voicing;
    }

    @Test
    void eachVoicePartGetsTheBestVoicing() {
        publish("Волк", "weak", 1);
        Voicing top = publish("Волк", "strong", 9);
        Voicing hoodVoicing = publish("Шапочка", "masha", 0);

        Cast cast = builder.best(book);

        assertEquals(top.id(), cast.voicingFor(wolf).orElseThrow());
        assertEquals(hoodVoicing.id(), cast.voicingFor(hood).orElseThrow());
    }

    @Test
    void voicePartWithoutVoicingsStaysUnvoiced() {
        publish("Волк", "sergey", 1);

        assertTrue(builder.best(book).voicingFor(hood).isEmpty());
    }

    @Test
    void draftsAreNotCast() {
        fixture.voicing(book, "Волк", "sergey", VoicingStatus.DRAFT);

        assertTrue(builder.best(book).voicingFor(wolf).isEmpty());
    }

    @Test
    void archivedVoicingsAreNotCast() {
        fixture.likes(fixture.voicing(book, "Волк", "sergey", VoicingStatus.ARCHIVED), 5);
        Voicing published = publish("Волк", "masha", 0);

        assertEquals(published.id(), builder.best(book).voicingFor(wolf).orElseThrow());
    }

    @Test
    void manualReplaceAndResetOfVoice() {
        Cast cast = Cast.empty().with(wolf, 42L);

        assertEquals(42L, cast.voicingFor(wolf).orElseThrow());
        assertTrue(cast.without(wolf).voicingFor(wolf).isEmpty());
    }
}
