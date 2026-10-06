package space.grayt.teremok.recording.messaging;

import java.time.Clock;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import space.grayt.teremok.events.RenderCompletedEvent;
import space.grayt.teremok.events.RoleRecordingPublishedEvent;
import space.grayt.teremok.events.TextWorkAddedEvent;
import space.grayt.teremok.events.UserCreatedEvent;
import space.grayt.teremok.recording.repository.RecordingRepository;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RecordingListeners {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final RecordingRepository repository;
    private final Clock clock;

    public RecordingListeners(RecordingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @KafkaListener(topics = TextWorkAddedEvent.TOPIC, groupId = "recordings-text-works")
    public void onTextWorkAdded(String json) throws Exception {
        repository.saveTextWork(JSON.readValue(json, TextWorkAddedEvent.class));
    }

    @KafkaListener(topics = UserCreatedEvent.TOPIC, groupId = "recordings-users")
    public void onUserCreated(String json) throws Exception {
        repository.saveUser(JSON.readValue(json, UserCreatedEvent.class).user().id());
    }

    @KafkaListener(topics = RoleRecordingPublishedEvent.TOPIC, groupId = "recordings-published-roles")
    public void onRoleRecordingPublished(String json) throws Exception {
        repository.savePublishedRole(JSON.readValue(json, RoleRecordingPublishedEvent.class));
    }

    @KafkaListener(topics = RenderCompletedEvent.TOPIC, groupId = "recordings-render-results")
    public void onRenderCompleted(String json) throws Exception {
        repository.completeRender(JSON.readValue(json, RenderCompletedEvent.class), Instant.now(clock));
    }
}
