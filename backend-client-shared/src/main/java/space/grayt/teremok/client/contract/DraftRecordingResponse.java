package space.grayt.teremok.client.contract;

import java.time.Instant;
import java.util.UUID;

public record DraftRecordingResponse(
        UUID id,
        UUID fragmentId,
        String originalFileName,
        String contentType,
        long sizeBytes,
        Instant createdAt) {
}
