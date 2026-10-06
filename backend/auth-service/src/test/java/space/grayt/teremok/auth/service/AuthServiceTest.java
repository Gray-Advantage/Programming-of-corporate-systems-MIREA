package space.grayt.teremok.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import space.grayt.teremok.auth.domain.UserAccount;
import space.grayt.teremok.auth.messaging.UserCreatedEventPublisher;
import space.grayt.teremok.auth.repository.UserAccountRepository;
import space.grayt.teremok.auth.security.JwtService;
import space.grayt.teremok.events.UserCreatedEvent;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:15:30Z");
    private static final String SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long";

    private FakeRepository repository;
    private CapturingPublisher publisher;
    private BCryptPasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new FakeRepository();
        publisher = new CapturingPublisher();
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(SECRET, Duration.ofDays(7), clock);
        authService = new AuthService(repository, publisher, passwordEncoder, jwtService, clock);
    }

    @Test
    void registrationStoresHashedPasswordPublishesEventAndLogsUserIn() {
        var result = authService.register(" reader ", "secret-password");

        var stored = repository.findByLogin("reader").orElseThrow();
        assertThat(stored.id()).isEqualTo(result.userId());
        assertThat(stored.passwordHash()).isNotEqualTo("secret-password");
        assertThat(passwordEncoder.matches("secret-password", stored.passwordHash())).isTrue();
        assertThat(stored.registeredAt()).isEqualTo(NOW);
        assertThat(jwtService.validateAndGetUserId(result.accessToken())).isEqualTo(result.userId());
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(publisher.event.user().id()).isEqualTo(result.userId());
        assertThat(publisher.event.user().registeredAt()).isEqualTo(NOW);
    }

    @Test
    void loginReturnsTokenForExistingUser() {
        var registered = authService.register("reader", "secret-password");

        var loggedIn = authService.login("reader", "secret-password");

        assertThat(loggedIn.userId()).isEqualTo(registered.userId());
        assertThat(jwtService.validateAndGetUserId(loggedIn.accessToken())).isEqualTo(registered.userId());
    }

    @Test
    void rejectsDuplicateLogin() {
        authService.register("reader", "secret-password");

        assertThatThrownBy(() -> authService.register("reader", "another-password"))
                .isInstanceOf(LoginAlreadyExistsException.class);
    }

    @Test
    void rejectsWrongPassword() {
        authService.register("reader", "secret-password");

        assertThatThrownBy(() -> authService.login("reader", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static final class FakeRepository implements UserAccountRepository {

        private final Map<String, UserAccount> users = new HashMap<>();

        @Override
        public void save(UserAccount user) {
            users.put(user.login(), user);
        }

        @Override
        public Optional<UserAccount> findByLogin(String login) {
            return Optional.ofNullable(users.get(login));
        }
    }

    private static final class CapturingPublisher implements UserCreatedEventPublisher {

        private UserCreatedEvent event;

        @Override
        public void publish(UserCreatedEvent event) {
            this.event = event;
        }
    }
}
