package space.grayt.teremok.catalog.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import space.grayt.teremok.catalog.domain.CatalogTextWork;
import space.grayt.teremok.catalog.repository.CatalogTextWorkRepository;
import space.grayt.teremok.events.TextWorkAddedEvent;

@Component
public class TextWorkAddedListener {

    private static final Logger log = LoggerFactory.getLogger(TextWorkAddedListener.class);

    private final CatalogTextWorkRepository repository;

    public TextWorkAddedListener(CatalogTextWorkRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = TextWorkAddedEvent.TOPIC,
            groupId = "catalog-service")
    public void onTextWorkAdded(TextWorkAddedEvent event) {
        repository.save(CatalogTextWork.from(event.textWork()));
        log.info("Text work {} saved in catalog", event.textWork().id());
    }
}
