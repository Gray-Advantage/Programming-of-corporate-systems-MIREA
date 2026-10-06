package space.grayt.teremok.client.contract;

import java.time.Instant;
import java.util.UUID;

public record PublishedRoleRecordingResponse(
        UUID roleRecordingId,
        Instant publishedAt) {
}
