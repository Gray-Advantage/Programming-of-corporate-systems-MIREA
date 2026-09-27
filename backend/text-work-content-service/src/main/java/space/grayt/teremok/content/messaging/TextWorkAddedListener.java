package space.grayt.teremok.content.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import space.grayt.teremok.content.domain.TextWorkContent;
import space.grayt.teremok.content.repository.TextWorkContentRepository;
import space.grayt.teremok.events.TextWorkAddedEvent;

@Component
public class TextWorkAddedListener {

    private static final Logger log = LoggerFactory.getLogger(TextWorkAddedListener.class);

    private final TextWorkContentRepository repository;

    public TextWorkAddedListener(TextWorkContentRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = TextWorkAddedEvent.TOPIC,
            groupId = "text-work-content-service-#{T(java.util.UUID).randomUUID()}")
    public void onTextWorkAdded(TextWorkAddedEvent event) {
        repository.save(TextWorkContent.from(event.textWork()));
        log.info("Text work {} content saved", event.textWork().id());
    }
}
