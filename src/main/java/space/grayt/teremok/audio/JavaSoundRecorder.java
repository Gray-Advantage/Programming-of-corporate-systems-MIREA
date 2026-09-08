package space.grayt.teremok.audio;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/** Запись с микрофона в WAV средствами JDK. Речевой формат: 16 кГц, 16 бит, моно. */
public final class JavaSoundRecorder implements AudioRecorder {

    public static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    public static final long MAX_MILLIS = 120_000;

    @Override
    public boolean isAvailable() {
        try {
            return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, FORMAT));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public RecordingSession start(Path target) {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new AudioUnavailableException("Микрофон недоступен: система не поддерживает запись 16 кГц моно");
        }
        try {
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();
            return new Session(line, target);
        } catch (LineUnavailableException e) {
            throw new AudioUnavailableException("Микрофон занят другой программой или недоступен", e);
        }
    }

    /** Пишет во временный файл и переносит его на место только после успешной остановки. */
    private static final class Session implements RecordingSession {

        private final TargetDataLine line;
        private final Path target;
        private final Path temp;
        private final Thread writer;
        private final AtomicBoolean recording = new AtomicBoolean(true);

        private Session(TargetDataLine line, Path target) {
            this.line = line;
            this.target = target;
            this.temp = target.resolveSibling(target.getFileName() + ".tmp");
            this.writer = new Thread(this::writeStream, "teremok-recorder");
            writer.setDaemon(true);
            writer.start();
            startWatchdog();
        }

        private void writeStream() {
            try (AudioInputStream stream = new AudioInputStream(line)) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, temp.toFile());
            } catch (IOException e) {
                // Закрытие линии в stop() обрывает поток — это штатное завершение записи.
            }
        }

        /** Забытый стоп не должен заливать диск: через MAX_MILLIS останавливаемся сами. */
        private void startWatchdog() {
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(MAX_MILLIS);
                    stop();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "teremok-recorder-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }

        @Override
        public boolean isRecording() {
            return recording.get();
        }

        @Override
        public void stop() {
            if (!recording.compareAndSet(true, false)) {
                return;
            }
            line.stop();
            line.close();
            try {
                writer.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                moveWithoutAtomicity();
            } catch (IOException e) {
                throw new AudioUnavailableException("Не удалось сохранить запись в " + target.getFileName(), e);
            }
        }

        private void moveWithoutAtomicity() {
            try {
                Files.move(temp, target, REPLACE_EXISTING);
            } catch (IOException e) {
                throw new AudioUnavailableException("Не удалось сохранить запись в " + target.getFileName(), e);
            }
        }
    }
}
