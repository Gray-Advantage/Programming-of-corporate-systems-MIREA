package space.grayt.teremok.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtVerifier verifier;
  private final java.net.http.HttpClient client =
    java.net.http.HttpClient.newBuilder()
      .connectTimeout(java.time.Duration.ofSeconds(3))
      .build();
  private final tools.jackson.databind.json.JsonMapper json =
    tools.jackson.databind.json.JsonMapper.builder().build();

  public JwtAuthenticationFilter(JwtVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      var userId = verifier.validateAndGetUserId(
        authorization.substring(BEARER_PREFIX.length())
      );
      var profileRequest = java.net.http.HttpRequest.newBuilder(
        java.net.URI.create(
          System.getenv().getOrDefault(
            "AUTH_SERVICE_URL",
            "http://auth-service:8080"
          ) + "/api/v1/auth/me"
        )
      )
        .header("Authorization", authorization)
        .header(
          "X-Correlation-ID",
          space.grayt.teremok.http.ApiErrors.correlation(request)
        )
        .timeout(java.time.Duration.ofSeconds(5))
        .GET()
        .build();
      java.net.http.HttpResponse<String> profileResponse;
      try {
        profileResponse = client.send(
          profileRequest,
          java.net.http.HttpResponse.BodyHandlers.ofString()
        );
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        space.grayt.teremok.http.ApiErrors.write(
          request,
          response,
          503,
          space.grayt.teremok.http.ApiErrors.message(503)
        );
        return;
      } catch (IOException e) {
        space.grayt.teremok.http.ApiErrors.write(
          request,
          response,
          503,
          space.grayt.teremok.http.ApiErrors.message(503)
        );
        return;
      }
      if (profileResponse.statusCode() != 200) {
        if (profileResponse.statusCode() >= 500) {
          space.grayt.teremok.http.ApiErrors.write(
            request,
            response,
            503,
            space.grayt.teremok.http.ApiErrors.message(503)
          );
        } else UnauthorizedResponseWriter.write(request, response);
        return;
      }
      var profile = json.readTree(profileResponse.body());
      boolean editor = false;
      boolean admin = false;
      var authorities =
        new java.util.ArrayList<org.springframework.security.core.authority.SimpleGrantedAuthority>();
      for (var role : profile.path("roles")) {
        authorities.add(
          new org.springframework.security.core.authority.SimpleGrantedAuthority(
            "ROLE_" + role.asString()
          )
        );
        if ("ADMIN".equals(role.asString())) admin = true;
      }
      for (var role : profile.path("roles"))
        if (
          "EDITOR".equals(role.asString()) || "ADMIN".equals(role.asString())
        ) editor = true;
      if (
        profile.path("mustChangePassword").asBoolean() ||
        (request.getRequestURI().endsWith("/openapi") && !admin) ||
        (!List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()) &&
          !editor)
      ) {
        space.grayt.teremok.http.ApiErrors.write(
          request,
          response,
          403,
          "Недостаточно прав или требуется смена временного пароля"
        );
        return;
      }
      SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(userId, null, authorities)
      );
      filterChain.doFilter(request, response);
    } catch (InvalidJwtException exception) {
      SecurityContextHolder.clearContext();
      UnauthorizedResponseWriter.write(request, response);
    }
  }
}
