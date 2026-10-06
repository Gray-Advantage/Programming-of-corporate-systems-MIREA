package space.grayt.teremok.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.auth.domain.UserAccount;
import space.grayt.teremok.auth.messaging.UserCreatedEventPublisher;
import space.grayt.teremok.auth.repository.UserAccountRepository;
import space.grayt.teremok.auth.security.JwtService;
import space.grayt.teremok.events.UserCreatedEvent;

@Service
public class AuthService {

    private static final int MAX_LOGIN_LENGTH = 100;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final UserAccountRepository repository;
    private final UserCreatedEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(
            UserAccountRepository repository,
            UserCreatedEventPublisher eventPublisher,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(String login, String password) {
        var normalizedLogin = validateAndNormalize(login, password);
        if (repository.findByLogin(normalizedLogin).isPresent()) {
            throw new LoginAlreadyExistsException(normalizedLogin);
        }

        var user = new UserAccount(
                UUID.randomUUID(),
                normalizedLogin,
                passwordEncoder.encode(password),
                Instant.now(clock));
        try {
            repository.save(user);
        } catch (DuplicateKeyException exception) {
            throw new LoginAlreadyExistsException(normalizedLogin);
        }

        eventPublisher.publish(new UserCreatedEvent(
                UUID.randomUUID(),
                1,
                user.registeredAt(),
                new UserCreatedEvent.UserPayload(user.id(), user.registeredAt())));
        return resultFor(user);
    }

    public AuthResult login(String login, String password) {
        var normalizedLogin = validateAndNormalize(login, password);
        var user = repository.findByLogin(normalizedLogin).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return resultFor(user);
    }

    private AuthResult resultFor(UserAccount user) {
        var token = jwtService.issue(user.id());
        return new AuthResult(user.id(), token.value(), token.expiresAt());
    }

    private static String validateAndNormalize(String login, String password) {
        if (login == null || login.isBlank()) {
            throw new InvalidCredentialsFormatException("Login must not be blank");
        }
        var normalizedLogin = login.trim();
        if (normalizedLogin.length() > MAX_LOGIN_LENGTH) {
            throw new InvalidCredentialsFormatException("Login must not be longer than 100 characters");
        }
        if (password == null || password.isBlank()) {
            throw new InvalidCredentialsFormatException("Password must not be blank");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new InvalidCredentialsFormatException("Password must not be longer than 72 UTF-8 bytes");
        }
        return normalizedLogin;
    }

    public record AuthResult(
            UUID userId,
            String accessToken,
            Instant expiresAt) {
    }
}
