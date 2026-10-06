package space.grayt.teremok.renderer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import space.grayt.teremok.events.RenderCompletedEvent;
import space.grayt.teremok.events.RenderRequestedEvent;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RenderRequestListener {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final FfmpegRenderer renderer;
    private final RenderResultPublisher publisher;

    public RenderRequestListener(FfmpegRenderer renderer, RenderResultPublisher publisher) {
        this.renderer = renderer;
        this.publisher = publisher;
    }

    @KafkaListener(topics = RenderRequestedEvent.TOPIC, groupId = "renderer-service")
    public void onRenderRequested(String json) throws Exception {
        var request = JSON.readValue(json, RenderRequestedEvent.class).renderRequest();
        try {
            var outputs = renderer.render(request);
            publish(request.id(), RenderCompletedEvent.Status.COMPLETED, outputs, null);
        } catch (Exception exception) {
            publish(request.id(), RenderCompletedEvent.Status.FAILED, List.of(), errorMessage(exception));
        }
    }

    private void publish(
            UUID renderId,
            RenderCompletedEvent.Status status,
            List<RenderCompletedEvent.OutputPayload> outputs,
            String error) {
        publisher.publish(new RenderCompletedEvent(
                UUID.randomUUID(),
                1,
                Instant.now(),
                new RenderCompletedEvent.RenderResultPayload(renderId, status, outputs, error)));
    }

    private static String errorMessage(Exception exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 2_000 ? message : message.substring(0, 2_000);
    }
}
