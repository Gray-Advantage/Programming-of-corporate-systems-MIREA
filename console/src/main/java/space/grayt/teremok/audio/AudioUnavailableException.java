package space.grayt.teremok.audio;

/** Проблема звукового тракта: устройство недоступно либо запись не сохранилась. */
public class AudioUnavailableException extends RuntimeException {

    public AudioUnavailableException(String message) {
        super(message);
    }

    public AudioUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
