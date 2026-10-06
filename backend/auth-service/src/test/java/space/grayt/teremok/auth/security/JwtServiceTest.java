package space.grayt.teremok.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long";
    private static final Instant NOW = Instant.parse("2026-10-06T10:15:30Z");

    @Test
    void issuesTokenWithUserIdAndSevenDayExpiration() {
        var userId = UUID.randomUUID();
        var service = serviceAt(NOW);

        var token = service.issue(userId);

        assertThat(service.validateAndGetUserId(token.value())).isEqualTo(userId);
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void rejectsExpiredToken() {
        var token = serviceAt(NOW).issue(UUID.randomUUID());
        var serviceAfterExpiration = serviceAt(NOW.plus(Duration.ofDays(8)));

        assertThatThrownBy(() -> serviceAfterExpiration.validateAndGetUserId(token.value()))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessage("JWT has expired");
    }

    @Test
    void rejectsTokenWithChangedPayload() {
        var token = serviceAt(NOW).issue(UUID.randomUUID()).value();
        var parts = token.split("\\.");
        var tampered = parts[0] + "." + parts[1] + "x." + parts[2];

        assertThatThrownBy(() -> serviceAt(NOW).validateAndGetUserId(tampered))
                .isInstanceOf(InvalidJwtException.class);
    }

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(SECRET, Duration.ofDays(7), Clock.fixed(instant, ZoneOffset.UTC));
    }
}
