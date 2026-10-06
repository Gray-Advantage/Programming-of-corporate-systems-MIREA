package space.grayt.teremok.auth.service;

public class InvalidCredentialsFormatException extends RuntimeException {

    public InvalidCredentialsFormatException(String message) {
        super(message);
    }
}
