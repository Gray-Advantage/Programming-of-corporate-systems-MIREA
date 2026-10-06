package space.grayt.teremok.auth.service;

public class LoginAlreadyExistsException extends RuntimeException {

    public LoginAlreadyExistsException(String login) {
        super("Login already exists: " + login);
    }
}
