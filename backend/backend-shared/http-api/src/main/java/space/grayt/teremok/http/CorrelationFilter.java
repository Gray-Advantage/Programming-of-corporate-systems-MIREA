package space.grayt.teremok.http;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws IOException, ServletException {
    response.setHeader(ApiErrors.HEADER, ApiErrors.correlation(request));
    try {
      chain.doFilter(request, response);
    } catch (Exception exception) {
      if (response.isCommitted()) throw exception;
      org.slf4j.LoggerFactory.getLogger(getClass()).error(
        "Filter failed: {}",
        ApiErrors.correlation(request),
        exception
      );
      response.resetBuffer();
      ApiErrors.write(request, response, 500, ApiErrors.message(500));
    }
  }
}
