package space.grayt.teremok.domain;

import java.time.Instant;
import java.util.Set;

/** A voicing: one voice part recorded by one author. The unit of publishing and voting. */
public record Voicing(String id, String textWorkId, String voicePartId, String authorId,
                      VoicingStatus status, Instant createdAt, Set<Integer> recordedFragments) {

    public Voicing {
        recordedFragments = Set.copyOf(recordedFragments);
    }

    public static String idOf(String textWorkId, String voicePartId, String authorId) {
        return textWorkId + "__" + voicePartId + "__" + authorId;
    }

    public static Voicing newDraft(String textWorkId, String voicePartId, String authorId, Instant createdAt) {
        return new Voicing(idOf(textWorkId, voicePartId, authorId), textWorkId, voicePartId, authorId,
                VoicingStatus.DRAFT, createdAt, Set.of());
    }

    public Voicing withStatus(VoicingStatus newStatus) {
        return new Voicing(id, textWorkId, voicePartId, authorId, newStatus, createdAt, recordedFragments);
    }

    public Voicing withRecordedFragments(Set<Integer> fragments) {
        return new Voicing(id, textWorkId, voicePartId, authorId, status, createdAt, fragments);
    }

    public boolean isRecorded(int fragmentNumber) {
        return recordedFragments.contains(fragmentNumber);
    }
}
