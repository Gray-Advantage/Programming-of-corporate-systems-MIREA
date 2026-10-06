package space.grayt.teremok.draft.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import space.grayt.teremok.draft.repository.DraftRepository;
import space.grayt.teremok.events.TextWorkAddedEvent;
import space.grayt.teremok.events.UserCreatedEvent;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProjectionListeners {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final DraftRepository repository;

    public ProjectionListeners(DraftRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = TextWorkAddedEvent.TOPIC, groupId = "draft-recordings-text-works")
    public void onTextWorkAdded(String json) throws Exception {
        repository.saveTextWork(JSON.readValue(json, TextWorkAddedEvent.class));
    }

    @KafkaListener(topics = UserCreatedEvent.TOPIC, groupId = "draft-recordings-users")
    public void onUserCreated(String json) throws Exception {
        repository.saveUser(JSON.readValue(json, UserCreatedEvent.class).user().id());
    }
}
