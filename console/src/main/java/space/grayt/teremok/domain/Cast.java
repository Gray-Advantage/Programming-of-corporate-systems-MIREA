package space.grayt.teremok.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Session casting: which voicing plays each speaker. No key means the speaker is read as text. */
public record Cast(Map<String, String> voicingBySpeaker) {

    public Cast {
        voicingBySpeaker = Map.copyOf(voicingBySpeaker);
    }

    public static Cast empty() {
        return new Cast(Map.of());
    }

    public Optional<String> voicingFor(String speakerId) {
        return Optional.ofNullable(voicingBySpeaker.get(speakerId));
    }

    public Cast with(String speakerId, String voicingId) {
        Map<String, String> updated = new LinkedHashMap<>(voicingBySpeaker);
        updated.put(speakerId, voicingId);
        return new Cast(updated);
    }

    public Cast without(String speakerId) {
        Map<String, String> updated = new LinkedHashMap<>(voicingBySpeaker);
        updated.remove(speakerId);
        return new Cast(updated);
    }
}
