package space.grayt.teremok.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String login,
        String passwordHash,
        Instant registeredAt) {
}
