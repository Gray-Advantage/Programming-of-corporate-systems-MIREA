package space.grayt.teremok.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioTest {

    @Test
    void фейковыйДиктофонПишетФайлПослеОстановки(@TempDir Path dir) throws Exception {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        Path target = dir.resolve("line-0001.wav");

        RecordingSession session = recorder.start(target);
        assertTrue(session.isRecording());
        session.stop();

        assertFalse(session.isRecording());
        assertTrue(Files.size(target) > 0);
        assertEquals(List.of(target), recorder.recorded());
    }

    @Test
    void повторнаяОстановкаБезопасна(@TempDir Path dir) {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        RecordingSession session = recorder.start(dir.resolve("line-0001.wav"));

        session.stop();
        session.stop();

        assertEquals(1, recorder.recorded().size());
    }

    @Test
    void недоступныйДиктофонСообщаетОбЭтом(@TempDir Path dir) {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        recorder.setAvailable(false);

        assertFalse(recorder.isAvailable());
        assertThrows(AudioUnavailableException.class, () -> recorder.start(dir.resolve("x.wav")));
    }

    @Test
    void фейковыйПлеерЗапоминаетЧтоИграл(@TempDir Path dir) {
        FakeAudioPlayer player = new FakeAudioPlayer();
        Path file = dir.resolve("line-0001.wav");

        player.play(file);

        assertEquals(List.of(file), player.played());
    }

    @Test
    void недоступныйПлеерСообщаетОбЭтом(@TempDir Path dir) {
        FakeAudioPlayer player = new FakeAudioPlayer();
        player.setAvailable(false);

        assertThrows(AudioUnavailableException.class, () -> player.play(dir.resolve("x.wav")));
    }

    @Test
    void проверкаДоступностиЖелезаНеБросаетИсключений() {
        assertDoesNotThrow(() -> new JavaSoundRecorder().isAvailable());
        assertDoesNotThrow(() -> new JavaSoundPlayer().isAvailable());
    }

    @Test
    void воспроизведениеОтсутствующегоФайлаДаётПонятнуюОшибку(@TempDir Path dir) {
        AudioUnavailableException error = assertThrows(AudioUnavailableException.class,
                () -> new JavaSoundPlayer().play(dir.resolve("нет.wav")));

        assertTrue(error.getMessage().contains("нет.wav"));
    }

    @Test
    void форматЗаписиРечевой() {
        assertEquals(16000f, JavaSoundRecorder.FORMAT.getSampleRate());
        assertEquals(16, JavaSoundRecorder.FORMAT.getSampleSizeInBits());
        assertEquals(1, JavaSoundRecorder.FORMAT.getChannels());
    }
}
