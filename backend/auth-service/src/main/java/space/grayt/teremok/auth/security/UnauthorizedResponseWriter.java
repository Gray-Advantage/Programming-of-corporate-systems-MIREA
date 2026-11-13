package space.grayt.teremok.auth.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import space.grayt.teremok.http.ApiErrors;

public final class UnauthorizedResponseWriter {

  private UnauthorizedResponseWriter() {}

  public static void write(
    HttpServletRequest request,
    HttpServletResponse response
  ) throws IOException {
    ApiErrors.write(request, response, 401, ApiErrors.message(401));
  }
}
