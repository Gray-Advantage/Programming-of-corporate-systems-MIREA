package space.grayt.teremok.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;

public interface VoicingRepository {

    Optional<Voicing> find(String voicingId);

    List<Voicing> findByBook(String bookId);

    List<Voicing> findByAuthor(String authorId);

    void save(Voicing voicing);

    void delete(String voicingId);

    /** Путь к файлу реплики. Файла может ещё не быть. */
    Path audioFile(String voicingId, int lineNumber);

    List<Vote> votes(String voicingId);

    void putVote(String voicingId, String profileId, VoteKind kind);

    void removeVote(String voicingId, String profileId);

    /** Сообщения о повреждённых ролях, накопленные при чтении. */
    List<String> warnings();
}
