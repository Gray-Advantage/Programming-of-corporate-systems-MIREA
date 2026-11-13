package space.grayt.teremok.http;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Covers servlet errors too, without Boot's default HTML or exception attributes. */
@RestController
public class ApiErrorController implements ErrorController {

  @RequestMapping("/error")
  public ResponseEntity<ApiError> error(HttpServletRequest request) {
    Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status =
      value instanceof Integer number && number >= 400 && number <= 599
        ? number
        : 500;
    return ApiErrors.response(
      request,
      status,
      ApiErrors.code(status),
      ApiErrors.message(status)
    );
  }
}
