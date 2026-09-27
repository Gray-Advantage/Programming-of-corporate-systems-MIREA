package space.grayt.teremok.deployer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import space.grayt.teremok.events.TextWorkAddedEvent;
import space.grayt.teremok.events.TextWorkAddedEvent.OriginType;
import tools.jackson.databind.json.JsonMapper;

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

    private static void publish(TextWorkAddedEvent event)
            throws ExecutionException, InterruptedException {
        Properties properties = new Properties();
        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) Duration.ofSeconds(60).toMillis());

        String payload = JSON.writeValueAsString(event);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                TextWorkAddedEvent.TOPIC,
                event.textWork().id().toString(),
                payload);

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
        require(event.eventId() != null, "eventId обязателен");
        require(event.eventVersion() == 1, "поддерживается только eventVersion=1");
        require(event.occurredAt() != null, "occurredAt обязателен");
        require(event.textWork() != null, "textWork обязателен");
        require(event.textWork().id() != null, "textWork.id обязателен");
        require(notBlank(event.textWork().name()), "textWork.name обязателен");
        require(event.textWork().authors() != null && !event.textWork().authors().isEmpty(),
                "textWork.authors не должен быть пустым");
        require(notBlank(event.textWork().language()), "textWork.language обязателен");
        require(event.textWork().origin() != null && event.textWork().origin().type() != null,
                "textWork.origin.type обязателен");
        require(event.textWork().segmentType() != null, "textWork.segmentType обязателен");
        require(event.textWork().segments() != null, "textWork.segments обязателен");
        require(event.textWork().voiceParts() != null, "textWork.voiceParts обязателен");

        if (event.textWork().origin().type() == OriginType.TRANSLATION) {
            require(notBlank(event.textWork().origin().translatedFrom()),
                    "для перевода обязателен origin.translatedFrom");
            require(event.textWork().origin().translators() != null
                            && !event.textWork().origin().translators().isEmpty(),
                    "для перевода обязателен хотя бы один переводчик");
        }
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
