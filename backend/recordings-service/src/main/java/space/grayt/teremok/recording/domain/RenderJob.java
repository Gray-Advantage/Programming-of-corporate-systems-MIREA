package space.grayt.teremok.recording.domain;

import java.time.Instant;
import java.util.UUID;

public record RenderJob(
        UUID id,
        UUID userId,
        UUID textWorkId,
        String outputMode,
        String status,
        Instant createdAt,
        Instant completedAt,
        String error) {
}
