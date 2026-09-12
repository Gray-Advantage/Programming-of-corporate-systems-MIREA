package space.grayt.teremok.app;

import java.nio.file.Path;
import space.grayt.teremok.domain.Line;

/** Шаг сеанса: реплика и то, чем её озвучивать. Если audio == null, реплика читается текстом. */
public record PlaybackStep(Line line, String speakerName, String authorId, Path audio) {

    public boolean isSpoken() {
        return audio != null;
    }
}
