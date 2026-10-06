package space.grayt.teremok.draft.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import space.grayt.teremok.security.JwtAuthenticationFilter;
import space.grayt.teremok.security.JwtVerifier;
import space.grayt.teremok.security.UnauthorizedResponseWriter;
import space.grayt.teremok.storage.MinioObjectStorage;
import space.grayt.teremok.storage.ObjectStorage;

@Configuration
public class DraftConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtVerifier jwtVerifier(@Value("${auth.jwt.secret}") String secret, Clock clock) {
        return new JwtVerifier(secret, clock);
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtVerifier verifier) {
        return new JwtAuthenticationFilter(verifier);
    }

    @Bean
    ObjectStorage objectStorage(
            @Value("${storage.endpoint}") String endpoint,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey,
            @Value("${storage.bucket}") String bucket) {
        return new MinioObjectStorage(endpoint, accessKey, secretKey, bucket);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> UnauthorizedResponseWriter.write(response)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
