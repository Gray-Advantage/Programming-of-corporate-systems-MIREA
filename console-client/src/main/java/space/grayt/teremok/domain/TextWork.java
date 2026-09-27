package space.grayt.teremok.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record TextWork(
        String id,
        String title,
        List<TextWorkFragment> fragments,
        Map<String, VoicePart> voicePartById) {

    public TextWork {
        fragments = List.copyOf(fragments);
        voicePartById = Map.copyOf(voicePartById);
    }

    /** Voice parts in order of their first fragment. */
    public List<VoicePart> voiceParts() {
        Map<String, VoicePart> ordered = new LinkedHashMap<>();
        for (TextWorkFragment fragment : fragments) {
            ordered.computeIfAbsent(fragment.voicePartId(), voicePartById::get);
        }
        return new ArrayList<>(ordered.values());
    }

    public List<TextWorkFragment> fragmentsOf(String voicePartId) {
        return fragments.stream()
                .filter(fragment -> fragment.voicePartId().equals(voicePartId))
                .toList();
    }

    public Optional<VoicePart> voicePart(String voicePartId) {
        return Optional.ofNullable(voicePartById.get(voicePartId));
    }
}
