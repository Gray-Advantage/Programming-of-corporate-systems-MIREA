package space.grayt.teremok.deployer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import space.grayt.teremok.events.TextWorkAddedEvent;
import space.grayt.teremok.events.TextWorkAddedEvent.OriginType;
import space.grayt.teremok.events.TextWorkAddedEvent.SegmentType;
import space.grayt.teremok.events.TextWorkAddedEvent.TextWorkPayload;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

public final class TextWorkDeployerApplication {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private TextWorkDeployerApplication() {
    }

    public static void main(String[] args) {
        try {
            String json = readJson(args);
            TextWorkAddedEvent event = JSON.readValue(json, TextWorkAddedEvent.class);
            validate(event);
            publish(event);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Публикация прервана.");
            System.exit(1);
        } catch (Exception exception) {
            System.err.println("Не удалось опубликовать text-work-added: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static String readJson(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Укажите не более одного пути к JSON-файлу.");
        }
        if (args.length == 1) {
            return Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        }

        System.out.println("Вставьте JSON события text-work-added.");
        System.out.println("После последней строки введите END и нажмите Enter:");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder json = new StringBuilder();
        String line;
        while ((line = input.readLine()) != null && !line.equals("END")) {
            json.append(line).append(System.lineSeparator());
        }
        if (json.isEmpty()) {
            throw new IllegalArgumentException("JSON не введён.");
        }
        return json.toString();
    }

    private static void publish(TextWorkAddedEvent event) throws ExecutionException, InterruptedException {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) Duration.ofSeconds(60).toMillis());

        String payload = JSON.writeValueAsString(event);
        ProducerRecord<String, String> record = new ProducerRecord<>(TextWorkAddedEvent.TOPIC, event.textWork().id().toString(), payload);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            var metadata = producer.send(record).get();
            System.out.printf(
                    "Опубликовано: topic=%s, partition=%d, offset=%d, textWorkId=%s%n",
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset(),
                    event.textWork().id());
        }
    }

    private static void validate(TextWorkAddedEvent event) {
        require(event != null, "событие обязательно");
        require(event.eventId() != null, "eventId обязателен");
        require(event.eventVersion() == 1, "поддерживается только eventVersion=1");
        require(event.occurredAt() != null, "occurredAt обязателен");
        require(event.textWork() != null, "textWork обязателен");

        TextWorkPayload textWork = event.textWork();
        require(textWork.id() != null, "textWork.id обязателен");
        require(notBlank(textWork.name()), "textWork.name обязателен");
        require(textWork.authors() != null && !textWork.authors().isEmpty(), "textWork.authors не должен быть пустым");
        require(textWork.authors().stream().allMatch(TextWorkDeployerApplication::notBlank), "textWork.authors не должен содержать пустые имена");
        require(notBlank(textWork.language()), "textWork.language обязателен");
        require(textWork.origin() != null && textWork.origin().type() != null, "textWork.origin.type обязателен");
        require(textWork.segmentType() != null, "textWork.segmentType обязателен");
        require(textWork.segments() != null && !textWork.segments().isEmpty(), "textWork.segments не должен быть пустым");
        require(textWork.voiceParts() != null && !textWork.voiceParts().isEmpty(), "textWork.voiceParts не должен быть пустым");

        validateOrigin(textWork);
        validateStructure(event.eventId(), textWork);
    }

    private static void validateOrigin(TextWorkPayload textWork) {
        var origin = textWork.origin();
        require(origin.translators() != null, "textWork.origin.translators обязателен");
        require(origin.translators().stream().allMatch(TextWorkDeployerApplication::notBlank),
                "textWork.origin.translators не должен содержать пустые имена");

        if (origin.type() == OriginType.TRANSLATION) {
            require(notBlank(origin.translatedFrom()), "для перевода обязателен origin.translatedFrom");
            require(!origin.translators().isEmpty(), "для перевода обязателен хотя бы один переводчик");
            return;
        }

        require(!notBlank(origin.translatedFrom()), "у оригинала origin.translatedFrom должен быть пустым");
        require(origin.translators().isEmpty(), "у оригинала origin.translators должен быть пустым");
    }

    private static void validateStructure(UUID eventId, TextWorkPayload textWork) {
        Set<UUID> ids = new HashSet<>();
        registerId(ids, eventId, "eventId");
        registerId(ids, textWork.id(), "textWork.id");

        Set<UUID> voicePartIds = new HashSet<>();
        Map<UUID, Integer> actualFragmentCounts = new HashMap<>();
        textWork.voiceParts().forEach(voicePart -> {
            require(voicePart != null, "textWork.voiceParts не должен содержать null");
            registerId(ids, voicePart.id(), "voicePart.id");
            voicePartIds.add(voicePart.id());
            require(notBlank(voicePart.name()), "voicePart.name обязателен");
            require(voicePart.totalFragmentsCount() > 0, "voicePart.totalFragmentsCount должен быть больше нуля: " + voicePart.id());
        });

        Set<Integer> segmentOrders = new HashSet<>();
        int fragmentsCount = 0;
        for (var segment : textWork.segments()) {
            require(segment != null, "textWork.segments не должен содержать null");
            registerId(ids, segment.id(), "segment.id");
            require(segment.orderInTextWork() > 0, "segment.orderInTextWork должен быть больше нуля: " + segment.id());
            require(segmentOrders.add(segment.orderInTextWork()), "дублируется segment.orderInTextWork=" + segment.orderInTextWork());
            require(notBlank(segment.name()), "segment.name обязателен: " + segment.id());
            require(segment.fragments() != null && !segment.fragments().isEmpty(), "segment.fragments не должен быть пустым: " + segment.id());

            Set<Integer> fragmentOrders = new HashSet<>();
            for (var fragment : segment.fragments()) {
                require(fragment != null, "segment.fragments не должен содержать null: " + segment.id());
                registerId(ids, fragment.id(), "voicePartFragment.id");
                require(fragment.orderInSegment() > 0, "fragment.orderInSegment должен быть больше нуля: " + fragment.id());
                require(fragmentOrders.add(fragment.orderInSegment()), "в сегменте " + segment.id() + " дублируется fragment.orderInSegment=" + fragment.orderInSegment());
                require(notBlank(fragment.content()), "fragment.content обязателен: " + fragment.id());
                require(fragment.voicePartId() != null, "fragment.voicePartId обязателен: " + fragment.id());
                require(voicePartIds.contains(fragment.voicePartId()), "фрагмент " + fragment.id() + " ссылается на неизвестный voicePart " + fragment.voicePartId());
                actualFragmentCounts.merge(fragment.voicePartId(), 1, Integer::sum);
                fragmentsCount++;
            }
        }

        require(fragmentsCount > 0, "textWork должен содержать хотя бы один фрагмент");
        if (textWork.segmentType() == SegmentType.SINGLE_SEGMENT) {
            require(textWork.segments().size() == 1, "для SINGLE_SEGMENT должен быть ровно один сегмент");
        }

        textWork.voiceParts().forEach(voicePart -> {
            int actual = actualFragmentCounts.getOrDefault(voicePart.id(), 0);
            require(voicePart.totalFragmentsCount() == actual,
                    "voicePart.totalFragmentsCount не совпадает с числом фрагментов для " + voicePart.id() + ": указано " + voicePart.totalFragmentsCount() + ", найдено " + actual);
        });
    }

    private static void registerId(Set<UUID> ids, UUID id, String field) {
        require(id != null, field + " обязателен");
        require(ids.add(id), "UUID " + id + " используется повторно (" + field + ")");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
