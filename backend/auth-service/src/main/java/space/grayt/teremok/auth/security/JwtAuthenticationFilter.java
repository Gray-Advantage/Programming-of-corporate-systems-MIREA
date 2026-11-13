package space.grayt.teremok.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final org.springframework.jdbc.core.JdbcTemplate db;

  public JwtAuthenticationFilter(
    JwtService jwtService,
    org.springframework.jdbc.core.JdbcTemplate db
  ) {
    this.jwtService = jwtService;
    this.db = db;
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
      var userId = jwtService.validateAndGetUserId(
        authorization.substring(BEARER_PREFIX.length())
      );
      if (
        db.queryForObject(
          "SELECT count(*) FROM user_account WHERE id=? AND active",
          Integer.class,
          userId
        ) == 0
      ) {
        UnauthorizedResponseWriter.write(request, response);
        return;
      }
      var roles = db.query(
        "SELECT role FROM user_role WHERE user_id=?",
        (r, n) ->
          new org.springframework.security.core.authority.SimpleGrantedAuthority(
            "ROLE_" + r.getString(1)
          ),
        userId
      );
      var authentication = new UsernamePasswordAuthenticationToken(
        userId,
        null,
        roles
      );
      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } catch (InvalidJwtException exception) {
      SecurityContextHolder.clearContext();
      UnauthorizedResponseWriter.write(request, response);
    }
  }
}
