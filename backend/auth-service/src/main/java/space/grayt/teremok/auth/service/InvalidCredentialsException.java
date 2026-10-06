package space.grayt.teremok.auth.service;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid login or password");
    }
}
