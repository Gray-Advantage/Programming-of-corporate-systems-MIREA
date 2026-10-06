package space.grayt.teremok.recording.domain;

import java.time.Instant;
import java.util.UUID;

public record PublishedRoleRecording(
        UUID id,
        UUID userId,
        UUID textWorkId,
        UUID roleId,
        Instant publishedAt) {
}
