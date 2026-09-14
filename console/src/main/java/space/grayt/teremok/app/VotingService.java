package space.grayt.teremok.app;

import java.util.List;
import java.util.Optional;
import space.grayt.teremok.domain.RatedVoicing;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.storage.VoicingRepository;

/** Score calculation and voting rules. */
public final class VotingService {

    private final VoicingRepository repository;

    public VotingService(VoicingRepository repository) {
        this.repository = repository;
    }

    public RatedVoicing rate(Voicing voicing) {
        List<Vote> votes = repository.votes(voicing.id());
        int likes = (int) votes.stream().filter(vote -> vote.kind() == VoteKind.LIKE).count();
        return new RatedVoicing(voicing, likes, votes.size() - likes);
    }

    /** Published voicings of a speaker, best first. */
    public List<RatedVoicing> ranked(String bookId, String speakerId) {
        return repository.findByBook(bookId).stream()
                .filter(voicing -> voicing.speakerId().equals(speakerId))
                .filter(voicing -> voicing.status() == VoicingStatus.PUBLISHED)
                .map(this::rate)
                .sorted(RatedVoicing.BEST_FIRST)
                .toList();
    }

    public Optional<RatedVoicing> rated(String voicingId) {
        return repository.find(voicingId).map(this::rate);
    }

    public Optional<VoteKind> voteOf(String voicingId, String voterId) {
        return repository.votes(voicingId).stream()
                .filter(vote -> vote.profileId().equals(voterId))
                .map(Vote::kind)
                .findFirst();
    }

    public VoteResult vote(String voicingId, String voterId, VoteKind kind) {
        Voicing voicing = repository.find(voicingId)
                .orElseThrow(() -> new IllegalArgumentException("Озвучка не найдена: " + voicingId));
        if (voicing.authorId().equals(voterId)) {
            return VoteResult.REJECTED_OWN;
        }
        if (voicing.status() != VoicingStatus.PUBLISHED) {
            return VoteResult.REJECTED_DRAFT;
        }
        Optional<VoteKind> current = voteOf(voicingId, voterId);
        if (current.isPresent() && current.get() == kind) {
            repository.removeVote(voicingId, voterId);
            return VoteResult.REMOVED;
        }
        repository.putVote(voicingId, voterId, kind);
        return current.isPresent() ? VoteResult.CHANGED : VoteResult.ADDED;
    }
}
