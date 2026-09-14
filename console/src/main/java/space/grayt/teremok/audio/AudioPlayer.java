package space.grayt.teremok.audio;

import java.nio.file.Path;

public interface AudioPlayer {

    boolean isAvailable();

    /** Plays the whole file and returns when it finishes. */
    void play(Path file);
}
