package space.grayt.teremok.audio;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Плеер для тестов: ничего не играет, только запоминает порядок файлов. */
public final class FakeAudioPlayer implements AudioPlayer {

    private final List<Path> played = new ArrayList<>();
    private boolean available = true;

    public void setAvailable(boolean value) {
        this.available = value;
    }

    public List<Path> played() {
        return List.copyOf(played);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void play(Path file) {
        if (!available) {
            throw new AudioUnavailableException("Устройство вывода недоступно");
        }
        played.add(file);
    }
}
