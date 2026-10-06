package space.grayt.teremok.draft.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import space.grayt.teremok.draft.service.DraftNotFoundException;
import space.grayt.teremok.draft.service.DraftValidationException;

@RestControllerAdvice
public class DraftExceptionHandler {

    @ExceptionHandler(DraftValidationException.class)
    public ResponseEntity<ErrorResponse> invalid(DraftValidationException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_draft", exception.getMessage()));
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(DraftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("not_found", exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("file_too_large", "Audio file exceeds the 100 MB limit"));
    }

    public record ErrorResponse(String code, String message) {
    }
}
