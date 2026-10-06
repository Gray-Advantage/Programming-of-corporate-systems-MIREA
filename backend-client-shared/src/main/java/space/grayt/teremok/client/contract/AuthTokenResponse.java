package space.grayt.teremok.client.contract;

import java.time.Instant;
import java.util.UUID;

public record AuthTokenResponse(
        UUID userId,
        String accessToken,
        String tokenType,
        Instant expiresAt) {
}
