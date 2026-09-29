package space.grayt.teremok.domain;

/**
 * DRAFT → PUBLISHED ⇄ ARCHIVED. Nothing returns to DRAFT: a published voicing already has listeners
 * and votes, so taking it down archives it instead.
 */
public enum VoicingStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean canMoveTo(VoicingStatus target) {
        return switch (this) {
            case DRAFT, ARCHIVED -> target == PUBLISHED;
            case PUBLISHED -> target == ARCHIVED;
        };
    }
}
