package space.grayt.teremok.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleRecordingPublishedEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        RoleRecordingPayload roleRecording) implements KafkaEvent {

    public static final String TOPIC = "role-recording-published";

    public record RoleRecordingPayload(
            UUID id,
            UUID userId,
            UUID textWorkId,
            UUID roleId,
            Instant publishedAt,
            List<FragmentRecordingPayload> fragments) {

        public RoleRecordingPayload {
            fragments = List.copyOf(fragments);
        }
    }

    public record FragmentRecordingPayload(
            UUID id,
            UUID fragmentId,
            String objectKey,
            String contentType,
            long sizeBytes) {
    }
}
