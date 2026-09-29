package space.grayt.teremok.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;

/**
 * Voicings with their recordings and votes. Methods that change a voicing throw
 * EntityNotFoundException when it does not exist.
 */
public interface VoicingRepository {

    Optional<Voicing> find(long id);

    /** In order of creation, as are the other lists. */
    List<Voicing> findByTextWork(String textWorkId);

    List<Voicing> findByAuthor(String authorId);

    /** An author has at most one voicing of a voice part: the schema has a unique key for it. */
    Optional<Voicing> findByVoicePartAndAuthor(String voicePartId, String authorId);

    /** Saves a new draft and returns it with the id the database generated. */
    Voicing insert(String voicePartId, String authorId, Instant createdAt);

    /** The publication time is set on the first publication and kept when the voicing is published again. */
    void updateStatus(long id, VoicingStatus status, Instant changedAt);

    /**
     * Publishes the drafts in one transaction. Each must still be a draft with every fragment of its voice
     * part recorded; otherwise nothing is published and BusinessRuleException is thrown.
     */
    void publishAll(List<Long> ids, Instant publishedAt);

    /** Deletes the voicing together with its recordings and votes. */
    void delete(long id);

    List<Vote> votes(long voicingId);

    void putVote(long voicingId, String profileId, VoteKind kind);

    void removeVote(long voicingId, String profileId);

    /** Stores the recording of a fragment; recording the fragment again replaces it. */
    void markRecorded(long voicingId, int fragmentNumber, String audioPath, int durationMs);

    /** Audio paths relative to the audio directory, by fragment number in ascending order. */
    Map<Integer, String> audioPaths(long voicingId);
}
