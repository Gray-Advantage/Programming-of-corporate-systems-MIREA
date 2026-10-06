package space.grayt.teremok.renderer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import space.grayt.teremok.events.RenderCompletedEvent;

@Component
public class RenderResultPublisher {

    private final KafkaTemplate<String, RenderCompletedEvent> kafkaTemplate;

    public RenderResultPublisher(KafkaTemplate<String, RenderCompletedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(RenderCompletedEvent event) {
        try {
            kafkaTemplate.send(
                            RenderCompletedEvent.TOPIC,
                            event.renderResult().renderId().toString(),
                            event)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing render result", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish render result", exception);
        }
    }
}
