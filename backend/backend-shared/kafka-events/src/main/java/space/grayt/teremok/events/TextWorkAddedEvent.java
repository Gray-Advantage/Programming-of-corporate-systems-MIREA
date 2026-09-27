package space.grayt.teremok.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TextWorkAddedEvent(
        UUID eventId,
        int eventVersion,
        Instant occurredAt,
        TextWorkPayload textWork) implements KafkaEvent {

    public static final String TOPIC = "text-work-added";

    public record TextWorkPayload(
            UUID id,
            List<String> authors,
            String name,
            LocalDate publicationDate,
            String language,
            TextWorkOrigin origin,
            SegmentType segmentType,
            List<SegmentPayload> segments,
            List<VoicePartPayload> voiceParts) {
    }

    public record TextWorkOrigin(
            OriginType type,
            String translatedFrom,
            List<String> translators) {
    }

    public enum OriginType {
        ORIGINAL,
        TRANSLATION
    }

    public enum SegmentType {
        CHAPTER,
        ACT,
        SINGLE_SEGMENT
    }

    public record SegmentPayload(
            UUID id,
            int orderInTextWork,
            String name,
            List<VoicePartFragmentPayload> fragments) {
    }

    public record VoicePartPayload(
            UUID id,
            String name,
            int totalFragmentsCount) {
    }

    public record VoicePartFragmentPayload(
            UUID id,
            int orderInSegment,
            String content,
            UUID voicePartId) {
    }
}
