package space.grayt.teremok.auth.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import space.grayt.teremok.events.UserCreatedEvent;

class UserCreatedEventSerializationTest {

    @Test
    void serializesEventForKafka() {
        var registeredAt = Instant.parse("2026-10-06T10:15:30Z");
        var event = new UserCreatedEvent(
                UUID.randomUUID(),
                1,
                registeredAt,
                new UserCreatedEvent.UserPayload(UUID.randomUUID(), registeredAt));

        try (var serializer = new JacksonJsonSerializer<UserCreatedEvent>()) {
            var json = new String(serializer.serialize(UserCreatedEvent.TOPIC, event), StandardCharsets.UTF_8);

            assertThat(json)
                    .contains("\"eventVersion\":1")
                    .contains("\"registeredAt\":\"2026-10-06T10:15:30Z\"");
        }
    }
}
