package space.grayt.teremok.exporter;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import space.grayt.teremok.security.*;

@SpringBootApplication
public class ExportHttpApplication {

  @Bean
  JwtAuthenticationFilter jwtFilter(
    @Value(
      "${JWT_SECRET:local-development-jwt-secret-change-before-production}"
    ) String secret
  ) {
    return new JwtAuthenticationFilter(
      new JwtVerifier(secret, Clock.systemUTC())
    );
  }

  @Bean
  SecurityFilterChain security(
    HttpSecurity http,
    JwtAuthenticationFilter filter
  ) throws Exception {
    return http
      .csrf(csrf -> csrf.disable())
      .logout(logout -> logout.disable())
      .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .exceptionHandling(errors ->
        errors.accessDeniedHandler((request, response, exception) -> space.grayt.teremok.http.ApiErrors.write(request, response, 403, space.grayt.teremok.http.ApiErrors.message(403))).authenticationEntryPoint((request, response, exception) ->
          UnauthorizedResponseWriter.write(request, response)
        )
      )
      .authorizeHttpRequests(requests ->
        requests
          .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
          .permitAll()
          .anyRequest()
          .authenticated()
      )
      .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
      .build();
  }
}
