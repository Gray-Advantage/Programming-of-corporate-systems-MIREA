package space.grayt.teremok.auth.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import space.grayt.teremok.events.UserCreatedEvent;

@Component
public class KafkaUserCreatedEventPublisher implements UserCreatedEventPublisher {

    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    public KafkaUserCreatedEventPublisher(KafkaTemplate<String, UserCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(UserCreatedEvent event) {
        try {
            kafkaTemplate.send(UserCreatedEvent.TOPIC, event.user().id().toString(), event).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing user-created event", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish user-created event", exception);
        }
    }
}
