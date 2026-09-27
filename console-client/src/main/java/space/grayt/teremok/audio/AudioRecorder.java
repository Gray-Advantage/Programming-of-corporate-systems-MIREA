package space.grayt.teremok.audio;

import java.nio.file.Path;

public interface AudioRecorder {

    boolean isAvailable();

    /** Starts recording into the given file. Throws AudioUnavailableException when there is no microphone. */
    RecordingSession start(Path target);
}
