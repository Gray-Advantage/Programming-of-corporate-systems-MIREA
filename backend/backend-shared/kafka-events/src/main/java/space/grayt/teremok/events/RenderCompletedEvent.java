package space.grayt.teremok.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RenderCompletedEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        RenderResultPayload renderResult) implements KafkaEvent {

    public static final String TOPIC = "render-responses";

    public enum Status {
        COMPLETED,
        FAILED
    }

    public record RenderResultPayload(
            UUID renderId,
            Status status,
            List<OutputPayload> outputs,
            String error) {

        public RenderResultPayload {
            outputs = List.copyOf(outputs);
        }
    }

    public record OutputPayload(
            UUID segmentId,
            String objectKey,
            String contentType) {
    }
}
