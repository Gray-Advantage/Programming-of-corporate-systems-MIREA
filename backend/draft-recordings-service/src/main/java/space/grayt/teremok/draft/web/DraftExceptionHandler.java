package space.grayt.teremok.draft.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import space.grayt.teremok.draft.service.*;
import space.grayt.teremok.http.*;

@RestControllerAdvice
@Order(0)
public class DraftExceptionHandler {

  @ExceptionHandler(DraftValidationException.class)
  public ResponseEntity<ApiError> invalid(
    DraftValidationException exception,
    HttpServletRequest request
  ) {
    return ApiErrors.response(
      request,
      400,
      "invalid_draft",
      exception.getMessage()
    );
  }

  @ExceptionHandler(DraftNotFoundException.class)
  public ResponseEntity<ApiError> notFound(
    DraftNotFoundException exception,
    HttpServletRequest request
  ) {
    return ApiErrors.response(
      request,
      404,
      "not_found",
      exception.getMessage()
    );
  }
}
