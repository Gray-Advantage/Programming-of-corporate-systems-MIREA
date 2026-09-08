package space.grayt.teremok.audio;

import java.nio.file.Path;

public interface AudioPlayer {

    boolean isAvailable();

    /** Проигрывает файл целиком и возвращает управление после окончания. */
    void play(Path file);
}
