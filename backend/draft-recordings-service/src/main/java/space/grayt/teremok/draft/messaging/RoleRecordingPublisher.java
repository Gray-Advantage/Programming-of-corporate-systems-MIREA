package space.grayt.teremok.draft.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import space.grayt.teremok.events.RoleRecordingPublishedEvent;

@Component
public class RoleRecordingPublisher {

    private final KafkaTemplate<String, RoleRecordingPublishedEvent> kafkaTemplate;

    public RoleRecordingPublisher(KafkaTemplate<String, RoleRecordingPublishedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(RoleRecordingPublishedEvent event) {
        try {
            kafkaTemplate.send(
                            RoleRecordingPublishedEvent.TOPIC,
                            event.roleRecording().id().toString(),
                            event)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing role recording", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish role recording", exception);
        }
    }
}
