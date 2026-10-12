package space.grayt.teremok.fx.audio;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.*;
import javafx.scene.media.*;

/** Local microphone capture; the completed WAV is uploaded to the backend by the controller. */
public final class AudioService implements AutoCloseable {
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);
    private TargetDataLine line;
    private Thread writer;
    private Path recording;
    private final AtomicReference<Exception> failure = new AtomicReference<>();
    private MediaPlayer player;

    public synchronized void startRecording() throws Exception {
        if (line != null) throw new IllegalStateException("Запись уже идёт");
        if (writer != null && writer.isAlive()) throw new IllegalStateException("Предыдущая запись ещё завершается");
        if (recording != null) Files.deleteIfExists(recording);
        failure.set(null);
        var info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) throw new IllegalStateException("Микрофон недоступен. Можно загрузить готовый аудиофайл.");
        recording = Files.createTempFile("teremok-microphone-", ".wav");
        try {
            line = (TargetDataLine)AudioSystem.getLine(info); line.open(FORMAT); line.start();
            var inputLine = line;
            writer = Thread.ofPlatform().daemon().name("teremok-microphone").start(() -> {
                try (var input = new AudioInputStream(inputLine)) {
                    AudioSystem.write(input, AudioFileFormat.Type.WAVE, recording.toFile());
                } catch (Exception ex) { failure.set(ex); }
            });
        } catch (Exception ex) {
            if (line != null) line.close(); line = null; Files.deleteIfExists(recording); recording = null; throw ex;
        }
    }
    public synchronized Path stopRecording() throws Exception {
        if (line == null) throw new IllegalStateException("Запись не начата");
        line.stop(); line.close(); line = null;
        writer.join(5000);
        if (writer.isAlive()) throw new IllegalStateException("Микрофон не завершил запись. Закройте окно и повторите попытку.");
        var path = recording;
        if (failure.get() != null) throw new IllegalStateException("Не удалось сохранить запись с микрофона", failure.get());
        if (Files.size(path) <= 44) throw new IllegalStateException("Запись пуста. Запишите хотя бы несколько секунд.");
        recording = null;
        return path;
    }
    public void play(Path file, java.util.function.Consumer<Throwable> onError, Runnable finished) {
        stopPlayback();
        try {
            var media = new Media(file.toUri().toString());
            var active = new MediaPlayer(media);
            player = active;
            active.setOnError(() -> {
                if (player != active) return;
                var error = active.getError(); stopPlayback(); onError.accept(error);
            });
            media.setOnError(() -> {
                if (player != active) return;
                var error = media.getError(); stopPlayback(); onError.accept(error);
            });
            active.setOnEndOfMedia(() -> {
                if (player != active) return;
                stopPlayback(); finished.run();
            });
            active.play();
        } catch (RuntimeException ex) { onError.accept(ex); }
    }
    public void stopPlayback() { if (player != null) { player.stop(); player.dispose(); player = null; } }
    @Override public synchronized void close() {
        stopPlayback();
        if (line != null) { line.stop(); line.close(); line = null; }
        try {
            if (writer != null) writer.join(5000);
            if (recording != null && (writer == null || !writer.isAlive())) Files.deleteIfExists(recording);
        } catch (Exception ex) { ex.printStackTrace(System.err); }
    }
}
