package space.grayt.teremok.storage;

/** Ошибка работы с файлами хранилища. Сообщение уже пригодно для показа пользователю. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
