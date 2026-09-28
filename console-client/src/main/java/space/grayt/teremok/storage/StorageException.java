package space.grayt.teremok.storage;

import space.grayt.teremok.exception.TeremokException;

/** The database or the audio directory failed. The message is ready to show to the user. */
public class StorageException extends TeremokException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
