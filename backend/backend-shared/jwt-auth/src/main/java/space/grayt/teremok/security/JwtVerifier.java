package space.grayt.teremok.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.json.JsonMapper;

public final class JwtVerifier {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Clock clock;

    public JwtVerifier(String secret, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        this.clock = clock;
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

    private byte[] sign(String value) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not verify JWT", exception);
        }
    }

    private record Header(String alg, String typ) {
    }

    private record Claims(String sub, long iat, long exp) {
    }
}
