package space.grayt.teremok.events;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        UserPayload user) implements KafkaEvent {

    public static final String TOPIC = "user-created";

    public record UserPayload(
            UUID id,
            Instant registeredAt) {
    }
}
