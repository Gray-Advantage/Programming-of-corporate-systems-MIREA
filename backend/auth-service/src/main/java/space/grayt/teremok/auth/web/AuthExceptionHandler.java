package space.grayt.teremok.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import space.grayt.teremok.auth.service.InvalidCredentialsException;
import space.grayt.teremok.auth.service.InvalidCredentialsFormatException;
import space.grayt.teremok.auth.service.LoginAlreadyExistsException;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsFormatException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(InvalidCredentialsFormatException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_credentials_format", exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableRequest() {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", "Request body is invalid"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("invalid_credentials", exception.getMessage()));
    }

    @ExceptionHandler(LoginAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> loginAlreadyExists(LoginAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("login_already_exists", exception.getMessage()));
    }

    public record ErrorResponse(String code, String message) {
    }
}
