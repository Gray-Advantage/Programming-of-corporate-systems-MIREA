package space.grayt.teremok.catalog.library.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class LibrarySecurity {

  public record Actor(
    UUID id,
    String login,
    String name,
    Set<String> roles,
    boolean active,
    boolean mustChangePassword
  ) {}

  @Bean
  SecurityFilterChain libraryChain(
    HttpSecurity http,
    @Value("${AUTH_SERVICE_URL:http://auth-service:8080}") String authUrl
  ) throws Exception {
    return http
      .csrf(c -> c.disable())
      .sessionManagement(s ->
        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(a ->
        a
          .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
          .permitAll()
          .requestMatchers("/actuator/health/**", "/api/v1/catalog/**")
          .permitAll()
          .requestMatchers(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
          )
          .hasRole("ADMIN")
          .requestMatchers(HttpMethod.GET, "/api/v1/library/**")
          .authenticated()
          .requestMatchers(
            "/api/v1/library/collections/**",
            "/api/v1/library/audit/**"
          )
          .hasRole("ADMIN")
          .anyRequest()
          .hasAnyRole("ADMIN", "EDITOR")
      )
      .exceptionHandling(e ->
        e
          .authenticationEntryPoint((r, s, x) ->
            error(r, s, 401, "Войдите в систему")
          )
          .accessDeniedHandler((r, s, x) ->
            error(r, s, 403, "Недостаточно прав")
          )
      )
      .addFilterBefore(
        new IdentityFilter(authUrl),
        UsernamePasswordAuthenticationFilter.class
      )
      .build();
  }

  static void error(
    HttpServletRequest request,
    HttpServletResponse response,
    int status,
    String message
  ) throws IOException {
    space.grayt.teremok.http.ApiErrors.write(
      request,
      response,
      status,
      message
    );
  }

  static class IdentityFilter extends OncePerRequestFilter {

    private final String url;
    private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3))
      .build();
    private final JsonMapper json = JsonMapper.builder().build();

    IdentityFilter(String url) {
      this.url = url;
    }

    @Override
    protected void doFilterInternal(
      HttpServletRequest r,
      HttpServletResponse s,
      FilterChain chain
    ) throws IOException, ServletException {
      String token = r.getHeader("Authorization");
      if (token != null && token.startsWith("Bearer ")) {
        try {
          var result = client.send(
            HttpRequest.newBuilder(URI.create(url + "/api/v1/auth/me"))
              .header("Authorization", token)
              .header(
                "X-Correlation-ID",
                space.grayt.teremok.http.ApiErrors.correlation(r)
              )
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build(),
            HttpResponse.BodyHandlers.ofString()
          );
          if (result.statusCode() != 200) {
            error(
              r,
              s,
              result.statusCode() >= 500 ? 503 : 401,
              result.statusCode() >= 500
                ? "Авторизация временно недоступна"
                : "Сеанс завершён. Войдите заново"
            );
            return;
          }
          var actor = json.readValue(result.body(), Actor.class);
          if (!actor.active() || actor.mustChangePassword()) {
            error(r, s, 403, "Сначала смените временный пароль");
            return;
          }
          SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
              actor,
              null,
              actor
                .roles()
                .stream()
                .map(x -> new SimpleGrantedAuthority("ROLE_" + x))
                .toList()
            )
          );
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          error(r, s, 503, "Авторизация временно недоступна");
          return;
        } catch (Exception e) {
          error(r, s, 503, "Авторизация временно недоступна");
          return;
        }
      }
      chain.doFilter(r, s);
    }
  }
}
