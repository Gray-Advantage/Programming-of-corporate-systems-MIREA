package space.grayt.teremok.audio;

/** An ongoing recording of one line. Calling stop() again is safe. */
public interface RecordingSession {

    void stop();

    boolean isRecording();
}
