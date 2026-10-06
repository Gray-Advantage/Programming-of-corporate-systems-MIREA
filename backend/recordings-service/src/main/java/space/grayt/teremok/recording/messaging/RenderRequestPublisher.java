package space.grayt.teremok.recording.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import space.grayt.teremok.events.RenderRequestedEvent;

@Component
public class RenderRequestPublisher {

    private final KafkaTemplate<String, RenderRequestedEvent> kafkaTemplate;

    public RenderRequestPublisher(KafkaTemplate<String, RenderRequestedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(RenderRequestedEvent event) {
        try {
            kafkaTemplate.send(
                            RenderRequestedEvent.TOPIC,
                            event.renderRequest().id().toString(),
                            event)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing render request", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish render request", exception);
        }
    }
}
