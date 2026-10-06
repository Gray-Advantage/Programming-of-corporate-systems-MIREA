package space.grayt.teremok.recording.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import space.grayt.teremok.recording.service.RecordingNotFoundException;
import space.grayt.teremok.recording.service.RecordingValidationException;

@RestControllerAdvice
public class RecordingExceptionHandler {

    @ExceptionHandler(RecordingValidationException.class)
    public ResponseEntity<ErrorResponse> invalid(RecordingValidationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_recording_selection", exception.getMessage()));
    }

    @ExceptionHandler(RecordingNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(RecordingNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("not_found", exception.getMessage()));
    }

    public record ErrorResponse(String code, String message) {
    }
}
