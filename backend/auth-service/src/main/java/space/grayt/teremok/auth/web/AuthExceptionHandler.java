package space.grayt.teremok.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import space.grayt.teremok.auth.service.*;
import space.grayt.teremok.http.*;

@RestControllerAdvice
@Order(0)
public class AuthExceptionHandler {

  @ExceptionHandler(InvalidCredentialsFormatException.class)
  public ResponseEntity<ApiError> invalidRequest(
    InvalidCredentialsFormatException exception,
    HttpServletRequest request
  ) {
    return ApiErrors.response(
      request,
      400,
      "invalid_credentials_format",
      exception.getMessage()
    );
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ApiError> invalidCredentials(
    InvalidCredentialsException exception,
    HttpServletRequest request
  ) {
    return ApiErrors.response(
      request,
      401,
      "invalid_credentials",
      exception.getMessage()
    );
  }

  @ExceptionHandler(LoginAlreadyExistsException.class)
  public ResponseEntity<ApiError> loginAlreadyExists(
    LoginAlreadyExistsException exception,
    HttpServletRequest request
  ) {
    return ApiErrors.response(
      request,
      409,
      "login_already_exists",
      exception.getMessage()
    );
  }
}
