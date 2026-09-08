package space.grayt.teremok.domain;

import java.time.Instant;
import java.util.Set;

/** Роль: озвучка одного персонажа одним автором. Единица публикации и голосования. */
public record Voicing(String id, String bookId, String speakerId, String authorId,
                      VoicingStatus status, Instant createdAt, Set<Integer> recordedLines) {

    public Voicing {
        recordedLines = Set.copyOf(recordedLines);
    }

    public static String idOf(String bookId, String speakerId, String authorId) {
        return bookId + "__" + speakerId + "__" + authorId;
    }

    public static Voicing newDraft(String bookId, String speakerId, String authorId, Instant createdAt) {
        return new Voicing(idOf(bookId, speakerId, authorId), bookId, speakerId, authorId,
                VoicingStatus.DRAFT, createdAt, Set.of());
    }

    public Voicing withStatus(VoicingStatus newStatus) {
        return new Voicing(id, bookId, speakerId, authorId, newStatus, createdAt, recordedLines);
    }

    public Voicing withRecordedLines(Set<Integer> lines) {
        return new Voicing(id, bookId, speakerId, authorId, status, createdAt, lines);
    }

    public boolean isRecorded(int lineNumber) {
        return recordedLines.contains(lineNumber);
    }
}
