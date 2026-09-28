package space.grayt.teremok.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Session casting: which voicing plays each voice part. No key means it is read as text. */
public record Cast(Map<String, Long> voicingByVoicePart) {

    public Cast {
        voicingByVoicePart = Map.copyOf(voicingByVoicePart);
    }

    public static Cast empty() {
        return new Cast(Map.of());
    }

    public Optional<Long> voicingFor(String voicePartId) {
        return Optional.ofNullable(voicingByVoicePart.get(voicePartId));
    }

    public Cast with(String voicePartId, long voicingId) {
        Map<String, Long> updated = new LinkedHashMap<>(voicingByVoicePart);
        updated.put(voicePartId, voicingId);
        return new Cast(updated);
    }

    public Cast without(String voicePartId) {
        Map<String, Long> updated = new LinkedHashMap<>(voicingByVoicePart);
        updated.remove(voicePartId);
        return new Cast(updated);
    }
}
