package space.grayt.teremok.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Test recorder: writes a short placeholder instead of sound. */
public final class FakeAudioRecorder implements AudioRecorder {

    private final List<Path> recorded = new ArrayList<>();
    private boolean available = true;

    public void setAvailable(boolean value) {
        this.available = value;
    }

    public List<Path> recorded() {
        return List.copyOf(recorded);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public RecordingSession start(Path target) {
        if (!available) {
            throw new AudioUnavailableException("Микрофон недоступен");
        }
        return new RecordingSession() {

            private boolean recording = true;

            @Override
            public boolean isRecording() {
                return recording;
            }

            @Override
            public void stop() {
                if (!recording) {
                    return;
                }
                recording = false;
                try {
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, "фейковая запись", StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new AudioUnavailableException("Не удалось сохранить запись", e);
                }
                recorded.add(target);
            }
        };
    }
}
