package space.grayt.teremok.app;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.VoicePart;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.storage.AudioStorage;
import space.grayt.teremok.storage.VoicingRepository;

/** Turns a text work and a cast into playback steps: audio where it exists, text elsewhere. */
public final class PlaybackService {

    private final VoicingRepository repository;
    private final AudioStorage audio;

    public PlaybackService(VoicingRepository repository, AudioStorage audio) {
        this.repository = repository;
        this.audio = audio;
    }

    public List<PlaybackStep> plan(TextWork textWork, Cast cast) {
        Map<String, Optional<Recorded>> byVoicePart = new HashMap<>();
        List<PlaybackStep> steps = new ArrayList<>(textWork.fragments().size());
        for (TextWorkFragment fragment : textWork.fragments()) {
            String voicePartName = textWork.voicePart(fragment.voicePartId())
                    .map(VoicePart::name)
                    .orElse(fragment.voicePartId());
            Optional<Recorded> recorded = byVoicePart.computeIfAbsent(fragment.voicePartId(),
                    voicePartId -> cast.voicingFor(voicePartId).flatMap(repository::find).map(this::recorded));
            Optional<Path> file = recorded.flatMap(found -> playable(found, fragment.number()));
            steps.add(new PlaybackStep(fragment, voicePartName,
                    file.isPresent() ? recorded.get().voicing().authorId() : null, file.orElse(null)));
        }
        return steps;
    }

    /** Pause long enough to read an unvoiced fragment. */
    public static Duration readingPause(String text) {
        long millis = Math.max(1200L, 60L * text.length());
        return Duration.ofMillis(Math.min(millis, 8000L));
    }

    /** First recorded fragment of a voicing, used by the play-sample command. */
    public Optional<Path> sample(Voicing voicing) {
        return repository.audioPaths(voicing.id()).values().stream()
                .findFirst()
                .flatMap(audio::resolve)
                .filter(AudioStorage::isPlayable);
    }

    /** One query for all recordings of a voicing rather than one per fragment. */
    private Recorded recorded(Voicing voicing) {
        return new Recorded(voicing, repository.audioPaths(voicing.id()));
    }

    private record Recorded(Voicing voicing, Map<Integer, String> audioPaths) {
    }

    /** A missing or empty file falls back to reading the fragment as text. */
    private Optional<Path> playable(Recorded recorded, int fragmentNumber) {
        return Optional.ofNullable(recorded.audioPaths().get(fragmentNumber))
                .flatMap(audio::resolve)
                .filter(AudioStorage::isPlayable);
    }
}
