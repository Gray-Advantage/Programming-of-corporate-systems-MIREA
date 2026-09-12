package space.grayt.teremok.audio;

import java.nio.file.Path;

public interface AudioRecorder {

    boolean isAvailable();

    /** Начинает запись в указанный файл. Бросает AudioUnavailableException, если микрофона нет. */
    RecordingSession start(Path target);
}
