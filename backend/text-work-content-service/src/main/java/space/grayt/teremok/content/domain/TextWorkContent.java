package space.grayt.teremok.content.domain;

import java.util.List;
import java.util.UUID;
import space.grayt.teremok.events.TextWorkAddedEvent.SegmentPayload;
import space.grayt.teremok.events.TextWorkAddedEvent.TextWorkPayload;
import space.grayt.teremok.events.TextWorkAddedEvent.VoicePartPayload;

public record TextWorkContent(
        UUID id,
        List<SegmentPayload> segments,
        List<VoicePartPayload> voiceParts) {

    public TextWorkContent {
        segments = List.copyOf(segments);
        voiceParts = List.copyOf(voiceParts);
    }

    public static TextWorkContent from(TextWorkPayload source) {
        return new TextWorkContent(source.id(), source.segments(), source.voiceParts());
    }
}
