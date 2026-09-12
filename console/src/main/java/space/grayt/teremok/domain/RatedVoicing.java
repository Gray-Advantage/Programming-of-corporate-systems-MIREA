package space.grayt.teremok.domain;

import java.util.Comparator;

/** Роль с подсчитанными голосами. Рейтинг — разница лайков и дизлайков. */
public record RatedVoicing(Voicing voicing, int likes, int dislikes) {

    /** Порядок в списке выбора: рейтинг, затем лайки, затем свежесть, затем id для устойчивости. */
    public static final Comparator<RatedVoicing> BEST_FIRST =
            Comparator.<RatedVoicing>comparingInt(RatedVoicing::score).reversed()
                    .thenComparing(Comparator.<RatedVoicing>comparingInt(RatedVoicing::likes).reversed())
                    .thenComparing(rated -> rated.voicing().createdAt(), Comparator.reverseOrder())
                    .thenComparing(rated -> rated.voicing().id());

    public int score() {
        return likes - dislikes;
    }
}
