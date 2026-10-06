package space.grayt.teremok.client.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RenderJobResponse(
        UUID id,
        UUID textWorkId,
        String outputMode,
        String status,
        Instant createdAt,
        Instant completedAt,
        String error,
        List<RenderOutput> outputs) {

    public RenderJobResponse {
        outputs = List.copyOf(outputs);
    }

    public record RenderOutput(
            UUID segmentId,
            String objectKey,
            String contentType) {
    }
}
