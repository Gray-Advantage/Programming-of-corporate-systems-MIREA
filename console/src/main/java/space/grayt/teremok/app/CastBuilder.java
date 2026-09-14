package space.grayt.teremok.app;

import java.util.LinkedHashMap;
import java.util.Map;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.RatedVoicing;
import space.grayt.teremok.domain.Speaker;

/** Builds a cast from the best published voicings so that just listening takes two keystrokes. */
public final class CastBuilder {

    private final VotingService voting;

    public CastBuilder(VotingService voting) {
        this.voting = voting;
    }

    public Cast best(Book book) {
        Map<String, String> chosen = new LinkedHashMap<>();
        for (Speaker speaker : book.speakers()) {
            voting.ranked(book.id(), speaker.id()).stream()
                    .findFirst()
                    .map(RatedVoicing::voicing)
                    .ifPresent(voicing -> chosen.put(speaker.id(), voicing.id()));
        }
        return new Cast(chosen);
    }
}
