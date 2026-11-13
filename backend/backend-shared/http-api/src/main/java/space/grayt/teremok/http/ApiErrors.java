package space.grayt.teremok.http;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import tools.jackson.databind.json.JsonMapper;

public final class ApiErrors {

  public static final String HEADER = "X-Correlation-ID";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ApiErrors() {}

  public static String correlation(HttpServletRequest request) {
    var current = request.getAttribute("correlationId");
    if (current instanceof String value && valid(value)) return value;
    String value = request.getHeader(HEADER);
    if (!valid(value)) value = UUID.randomUUID().toString();
    request.setAttribute("correlationId", value);
    return value;
  }

  private static boolean valid(String value) {
    return value != null && value.matches("[a-zA-Z0-9-]{1,64}");
  }

  public static ResponseEntity<ApiError> response(
    HttpServletRequest request,
    int status,
    String code,
    String message
  ) {
    String correlation = correlation(request);
    return ResponseEntity.status(status)
      .contentType(MediaType.APPLICATION_JSON)
      .header(HEADER, correlation)
      .body(new ApiError(status, code, message, correlation));
  }

  public static void write(
    HttpServletRequest request,
    HttpServletResponse response,
    int status,
    String message
  ) throws IOException {
    String correlation = correlation(request);
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader(HEADER, correlation);
    response
      .getWriter()
      .write(
        JSON.writeValueAsString(
          new ApiError(status, code(status), message, correlation)
        )
      );
  }

  public static String code(int status) {
    return switch (status) {
      case 400 -> "bad_request";
      case 401 -> "unauthorized";
      case 403 -> "forbidden";
      case 404 -> "not_found";
      case 405 -> "method_not_allowed";
      case 406 -> "not_acceptable";
      case 409 -> "conflict";
      case 413 -> "file_too_large";
      case 415 -> "unsupported_media_type";
      case 429 -> "too_many_requests";
      case 503 -> "service_unavailable";
      default -> status >= 500 ? "internal_error" : "request_rejected";
    };
  }

  public static String message(int status) {
    return switch (status) {
      case 400 -> "Проверьте значения полей и формат запроса";
      case 401 -> "Войдите в систему";
      case 403 -> "Недостаточно прав для этого действия";
      case 404 -> "Запись или адрес не найдены";
      case 405 -> "Этот способ обращения к адресу не поддерживается";
      case 406 -> "Запрошенный формат ответа не поддерживается";
      case 409 -> "Данные изменились или запись уже существует. Обновите страницу";
      case 413 -> "Файл превышает допустимый размер";
      case 415 -> "Формат запроса не поддерживается";
      case 429 -> "Слишком много запросов. Повторите позже";
      case 503 -> "Сервис временно недоступен. Повторите попытку";
      default -> "Не удалось выполнить действие. Повторите попытку";
    };
  }
}
