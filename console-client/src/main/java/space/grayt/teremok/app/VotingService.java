package space.grayt.teremok.app;

import java.util.List;
import java.util.Optional;
import space.grayt.teremok.domain.RatedVoicing;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicingStatus;
import space.grayt.teremok.domain.Vote;
import space.grayt.teremok.domain.VoteKind;
import space.grayt.teremok.exception.EntityNotFoundException;
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

    /** Published voicings of a voice part, best first. Drafts and archived voicings are not offered. */
    public List<RatedVoicing> ranked(String textWorkId, String voicePartId) {
        return repository.findByTextWork(textWorkId).stream()
                .filter(voicing -> voicing.voicePartId().equals(voicePartId))
                .filter(voicing -> voicing.status() == VoicingStatus.PUBLISHED)
                .map(this::rate)
                .sorted(RatedVoicing.BEST_FIRST)
                .toList();
    }

    public Optional<RatedVoicing> rated(long voicingId) {
        return repository.find(voicingId).map(this::rate);
    }

    public Optional<VoteKind> voteOf(long voicingId, String voterId) {
        return repository
                .votes(voicingId)
                .stream()
                .filter(vote -> vote.profileId().equals(voterId))
                .map(Vote::kind)
                .findFirst();
    }

    /** Only published voicings take votes, and never from their author; an archived one counts as a draft. */
    public VoteResult vote(long voicingId, String voterId, VoteKind kind) {
        Voicing voicing = repository.find(voicingId)
                .orElseThrow(() -> new EntityNotFoundException("Озвучка не найдена: возможно, её уже удалили."));
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
