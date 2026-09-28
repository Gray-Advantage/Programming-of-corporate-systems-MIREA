package space.grayt.teremok.exception;

/**
 * Base of the application's own exceptions. The message is always written for the user, so the
 * console prints it as is and goes on instead of showing a stack trace.
 */
public abstract class TeremokException extends RuntimeException {

    protected TeremokException(String message) {
        super(message);
    }

    protected TeremokException(String message, Throwable cause) {
        super(message, cause);
    }
}
