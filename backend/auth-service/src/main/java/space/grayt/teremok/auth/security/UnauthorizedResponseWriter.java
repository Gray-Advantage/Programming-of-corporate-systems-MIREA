package space.grayt.teremok.auth.security;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

final class UnauthorizedResponseWriter {

    private UnauthorizedResponseWriter() {
    }

    static void write(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"unauthorized\",\"message\":\"Authentication is required\"}");
    }
}
