package space.grayt.teremok.exception;

/** A voicing, profile or fragment the operation needs does not exist, for example it was just deleted. */
public class EntityNotFoundException extends TeremokException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
