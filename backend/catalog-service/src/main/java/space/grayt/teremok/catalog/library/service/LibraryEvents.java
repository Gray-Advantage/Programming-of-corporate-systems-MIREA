package space.grayt.teremok.catalog.library.service;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import space.grayt.teremok.catalog.library.repository.Works;
import space.grayt.teremok.catalog.repository.CatalogTextWorkRepository;
import space.grayt.teremok.client.contract.TextWorkContentResponse;
import space.grayt.teremok.events.TextWorkAddedEvent;
import space.grayt.teremok.events.TextWorkAddedEvent.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LibraryEvents {

  private final JdbcTemplate db;
  private final KafkaTemplate<String, Object> kafka;
  private final LibraryService library;
  private final Works works;
  private final CatalogTextWorkRepository catalog;
  private final JsonMapper json = JsonMapper.builder().build();
  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(3))
    .build();

  public LibraryEvents(
    JdbcTemplate db,
    KafkaTemplate<String, Object> kafka,
    LibraryService library,
    Works works,
    CatalogTextWorkRepository catalog
  ) {
    this.db = db;
    this.kafka = kafka;
    this.library = library;
    this.works = works;
    this.catalog = catalog;
  }

  @Scheduled(fixedDelay = 2000)
  public void publish() {
    for (var row : db.queryForList(
      "SELECT id,payload FROM library_outbox ORDER BY created_at LIMIT 20"
    )) {
      try {
        var event = json.readValue(
          row.get("payload").toString(),
          TextWorkAddedEvent.class
        );
        kafka
          .send(
            TextWorkAddedEvent.TOPIC,
            event.textWork().id().toString(),
            event
          )
          .get(10, TimeUnit.SECONDS);
        db.update("DELETE FROM library_outbox WHERE id=?", row.get("id"));
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn(
          "Library outbox will retry",
          e
        );
        break;
      }
    }
  }

  @Scheduled(initialDelay = 15000, fixedDelay = 60000)
  public void importExisting() {
    for (var item : catalog.findAll()) {
      if (works.existsById(item.id()) || library.isDeleted(item.id())) continue;
      try {
        var response = http.send(
          HttpRequest.newBuilder(
            URI.create(
              "http://text-work-content-service:8080/api/v1/text-work-content/text-works/" +
                item.id()
            )
          )
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build(),
          HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != 200) continue;
        var content = json.readValue(
          response.body(),
          TextWorkContentResponse.class
        );
        var segments = content
          .segments()
          .stream()
          .map(s ->
            new SegmentPayload(
              s.id(),
              s.orderInTextWork(),
              s.name(),
              s
                .fragments()
                .stream()
                .map(f ->
                  new VoicePartFragmentPayload(
                    f.id(),
                    f.orderInSegment(),
                    f.content(),
                    f.voicePartId()
                  )
                )
                .toList()
            )
          )
          .toList();
        var roles = content
          .voiceParts()
          .stream()
          .map(r ->
            new VoicePartPayload(r.id(), r.name(), r.totalFragmentsCount())
          )
          .toList();
        var origin = new TextWorkOrigin(
          OriginType.valueOf(item.origin().type().name()),
          item.origin().translatedFrom(),
          item.origin().translators()
        );
        library.ingest(
          new TextWorkAddedEvent(
            UUID.randomUUID(),
            1,
            Instant.now(),
            new TextWorkPayload(
              item.id(),
              item.authors(),
              item.name(),
              item.publicationDate(),
              item.language(),
              origin,
              SegmentType.valueOf(item.segmentType().name()),
              segments,
              roles
            )
          )
        );
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn(
          "Existing work import will retry: {}",
          item.id()
        );
      }
    }
  }
}
