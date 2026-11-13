package space.grayt.teremok.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
    Exception exception,
    Object body,
    HttpHeaders headers,
    HttpStatusCode status,
    WebRequest request
  ) {
    var response = ApiErrors.response(
      ((ServletWebRequest) request).getRequest(),
      status.value(),
      ApiErrors.code(status.value()),
      ApiErrors.message(status.value())
    );
    HttpHeaders result = new HttpHeaders();
    result.putAll(headers);
    result.putAll(response.getHeaders());
    return new ResponseEntity<>(response.getBody(), result, status);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> status(
    ResponseStatusException exception,
    HttpServletRequest request
  ) {
    int status = exception.getStatusCode().value();
    return ApiErrors.response(
      request,
      status,
      ApiErrors.code(status),
      status >= 500 || exception.getReason() == null
        ? ApiErrors.message(status)
        : exception.getReason()
    );
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class,
  })
  public ResponseEntity<ApiError> conflict(
    Exception exception,
    HttpServletRequest request
  ) {
    return ApiErrors.response(request, 409, "conflict", ApiErrors.message(409));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    ConstraintViolationException.class,
    org.springframework.web.multipart.MultipartException.class,
  })
  public ResponseEntity<ApiError> validation(
    Exception exception,
    HttpServletRequest request
  ) {
    return ApiErrors.response(
      request,
      400,
      "bad_request",
      ApiErrors.message(400)
    );
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(HttpServletRequest request) {
    return ApiErrors.response(
      request,
      403,
      "forbidden",
      ApiErrors.message(403)
    );
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> unauthorized(HttpServletRequest request) {
    return ApiErrors.response(
      request,
      401,
      "unauthorized",
      ApiErrors.message(401)
    );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> internal(
    Exception exception,
    HttpServletRequest request
  ) {
    LoggerFactory.getLogger(getClass()).error(
      "Request failed: {}",
      ApiErrors.correlation(request),
      exception
    );
    return ApiErrors.response(
      request,
      500,
      "internal_error",
      ApiErrors.message(500)
    );
  }
}
