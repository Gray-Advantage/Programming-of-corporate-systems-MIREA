package space.grayt.teremok.events;

import java.time.Instant;
import java.util.UUID;

public sealed interface KafkaEvent permits
        TextWorkAddedEvent,
        UserCreatedEvent,
        RoleRecordingPublishedEvent,
        RenderRequestedEvent,
        RenderCompletedEvent {

    UUID eventId();

    int eventVersion();

    Instant occurredAt();
}
