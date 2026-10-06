package space.grayt.teremok.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class JwtService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.ttl:PT168H}") Duration ttl,
            Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("JWT ttl must be positive");
        }
        this.ttl = ttl;
        this.clock = clock;
    }

    public IssuedToken issue(UUID userId) {
        var issuedAt = Instant.now(clock);
        var expiresAt = issuedAt.plus(ttl);
        var header = encodeJson(new Header("HS256", "JWT"));
        var payload = encodeJson(new Claims(userId.toString(), issuedAt.getEpochSecond(), expiresAt.getEpochSecond()));
        var unsignedToken = header + "." + payload;
        var signature = BASE64_URL_ENCODER.encodeToString(sign(unsignedToken));
        return new IssuedToken(unsignedToken + "." + signature, expiresAt);
    }

    public UUID validateAndGetUserId(String token) {
        try {
            var parts = token.split("\\.", -1);
            if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
                throw new InvalidJwtException("Malformed JWT");
            }

            var expectedSignature = sign(parts[0] + "." + parts[1]);
            var actualSignature = BASE64_URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new InvalidJwtException("Invalid JWT signature");
            }

            var header = JSON.readValue(BASE64_URL_DECODER.decode(parts[0]), Header.class);
            if (!"HS256".equals(header.alg()) || !"JWT".equals(header.typ())) {
                throw new InvalidJwtException("Unsupported JWT header");
            }

            var claims = JSON.readValue(BASE64_URL_DECODER.decode(parts[1]), Claims.class);
            if (claims.exp() <= Instant.now(clock).getEpochSecond()) {
                throw new InvalidJwtException("JWT has expired");
            }
            return UUID.fromString(claims.sub());
        } catch (InvalidJwtException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidJwtException("Invalid JWT", exception);
        }
    }

    private String encodeJson(Object value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(JSON.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create JWT", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    private record Header(String alg, String typ) {
    }

    private record Claims(String sub, long iat, long exp) {
    }
}
