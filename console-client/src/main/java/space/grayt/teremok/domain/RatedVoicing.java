package space.grayt.teremok.domain;

import java.util.Comparator;

/** A voicing with counted votes. The score is likes minus dislikes. */
public record RatedVoicing(Voicing voicing, int likes, int dislikes) {

    /** Order in the choice list: score, then likes, then recency, then id for a stable order. */
    public static final Comparator<RatedVoicing> BEST_FIRST =
            Comparator.<RatedVoicing>comparingInt(RatedVoicing::score).reversed()
                    .thenComparing(Comparator.<RatedVoicing>comparingInt(RatedVoicing::likes).reversed())
                    .thenComparing(rated -> rated.voicing().createdAt(), Comparator.reverseOrder())
                    .thenComparing(rated -> rated.voicing().id());

    public int score() {
        return likes - dislikes;
    }
}
