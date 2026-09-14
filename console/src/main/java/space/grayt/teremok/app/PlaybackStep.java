package space.grayt.teremok.app;

import java.nio.file.Path;
import space.grayt.teremok.domain.Line;

/** One playback step: a line and how to voice it. When audio is null, the line is read as text. */
public record PlaybackStep(Line line, String speakerName, String authorId, Path audio) {

    public boolean isSpoken() {
        return audio != null;
    }
}
