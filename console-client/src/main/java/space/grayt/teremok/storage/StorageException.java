package space.grayt.teremok.storage;

/** Storage file error. The message is ready to show to the user. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
