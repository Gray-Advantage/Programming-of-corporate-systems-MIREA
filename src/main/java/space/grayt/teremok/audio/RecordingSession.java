package space.grayt.teremok.audio;

/** Идущая запись одной реплики. Повторный stop() безопасен. */
public interface RecordingSession {

    void stop();

    boolean isRecording();
}
