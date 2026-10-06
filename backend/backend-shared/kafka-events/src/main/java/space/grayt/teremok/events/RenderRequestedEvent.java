package space.grayt.teremok.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RenderRequestedEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        RenderRequestPayload renderRequest) implements KafkaEvent {

    public static final String TOPIC = "render-requests";

    public enum OutputMode {
        SINGLE_FILE,
        BY_SEGMENTS
    }

    public record RenderRequestPayload(
            UUID id,
            UUID userId,
            UUID textWorkId,
            OutputMode outputMode,
            List<SegmentAudioPayload> segments) {

        public RenderRequestPayload {
            segments = List.copyOf(segments);
        }
    }

    public record SegmentAudioPayload(
            UUID id,
            int orderInTextWork,
            List<FragmentAudioPayload> fragments) {

        public SegmentAudioPayload {
            fragments = List.copyOf(fragments);
        }
    }

    public record FragmentAudioPayload(
            UUID id,
            int orderInSegment,
            String objectKey) {
    }
}
