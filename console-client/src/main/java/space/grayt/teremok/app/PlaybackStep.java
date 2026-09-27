package space.grayt.teremok.app;

import java.nio.file.Path;
import space.grayt.teremok.domain.TextWorkFragment;

/** One playback step: a fragment and how to voice it. Null audio means reading as text. */
public record PlaybackStep(TextWorkFragment fragment, String voicePartName, String authorId, Path audio) {

    public boolean isSpoken() {
        return audio != null;
    }
}
