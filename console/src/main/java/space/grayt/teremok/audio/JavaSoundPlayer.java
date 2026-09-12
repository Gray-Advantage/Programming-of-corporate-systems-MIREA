package space.grayt.teremok.audio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Воспроизведение WAV через Clip. Метод play блокирует до конца реплики. */
public final class JavaSoundPlayer implements AudioPlayer {

    @Override
    public boolean isAvailable() {
        try {
            return AudioSystem.isLineSupported(new DataLine.Info(Clip.class, JavaSoundRecorder.FORMAT));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void play(Path file) {
        Clip clip = null;
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file.toFile())) {
            clip = AudioSystem.getClip();
            CountDownLatch finished = new CountDownLatch(1);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    finished.countDown();
                }
            });
            clip.open(stream);
            clip.start();
            finished.await();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            throw new AudioUnavailableException("Не удалось воспроизвести " + file.getFileName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (clip != null) {
                clip.close();
            }
        }
    }
}
