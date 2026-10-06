package space.grayt.teremok.storage;

public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(Throwable cause) {
        super("Object storage operation failed", cause);
    }
}
