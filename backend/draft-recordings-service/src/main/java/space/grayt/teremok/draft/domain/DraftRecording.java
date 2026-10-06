package space.grayt.teremok.draft.domain;

import java.time.Instant;
import java.util.UUID;

public record DraftRecording(
        UUID id,
        UUID userId,
        UUID fragmentId,
        String objectKey,
        String originalFileName,
        String contentType,
        long sizeBytes,
        Instant createdAt) {
}
