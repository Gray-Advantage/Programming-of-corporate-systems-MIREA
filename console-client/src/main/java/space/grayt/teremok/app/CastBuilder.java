package space.grayt.teremok.app;

import java.util.LinkedHashMap;
import java.util.Map;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.RatedVoicing;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.VoicePart;

/** Builds a cast from the best published voicings so that just listening takes two keystrokes. */
public final class CastBuilder {

    private final VotingService voting;

    public CastBuilder(VotingService voting) {
        this.voting = voting;
    }

    public Cast best(TextWork textWork) {
        Map<String, Long> chosen = new LinkedHashMap<>();
        for (VoicePart voicePart : textWork.voiceParts()) {
            voting.ranked(textWork.id(), voicePart.id()).stream()
                    .findFirst()
                    .map(RatedVoicing::voicing)
                    .ifPresent(voicing -> chosen.put(voicePart.id(), voicing.id()));
        }
        return new Cast(chosen);
    }
}
