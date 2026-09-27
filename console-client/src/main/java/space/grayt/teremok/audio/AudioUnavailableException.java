package space.grayt.teremok.audio;

/** Audio failure: the device is unavailable or the recording could not be saved. */
public class AudioUnavailableException extends RuntimeException {

    public AudioUnavailableException(String message) {
        super(message);
    }

    public AudioUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
