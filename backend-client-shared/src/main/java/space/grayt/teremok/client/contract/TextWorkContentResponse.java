package space.grayt.teremok.client.contract;

import java.util.List;
import java.util.UUID;

public record TextWorkContentResponse(
        UUID id,
        List<Segment> segments,
        List<VoicePart> voiceParts) {

    public TextWorkContentResponse {
        segments = List.copyOf(segments);
        voiceParts = List.copyOf(voiceParts);
    }

    public record Segment(
            UUID id,
            int orderInTextWork,
            String name,
            List<VoicePartFragment> fragments) {

        public Segment {
            fragments = List.copyOf(fragments);
        }
    }

    public record VoicePart(
            UUID id,
            String name,
            int totalFragmentsCount) {
    }

    public record VoicePartFragment(
            UUID id,
            int orderInSegment,
            String content,
            UUID voicePartId) {
    }
}
