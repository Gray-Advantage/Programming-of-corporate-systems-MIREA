package space.grayt.teremok.recording.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import space.grayt.teremok.http.*;
import space.grayt.teremok.recording.service.*;

@RestControllerAdvice
@Order(0)
public class RecordingExceptionHandler {

  @ExceptionHandler(RecordingValidationException.class)
  public ResponseEntity<ApiError> invalid(
    RecordingValidationException exception,
    HttpServletRequest request
  ) {
    return ApiErrors.response(
      request,
      400,
      "invalid_recording_selection",
      exception.getMessage()
    );
  }

  @ExceptionHandler(RecordingNotFoundException.class)
  public ResponseEntity<ApiError> notFound(
    RecordingNotFoundException exception,
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
