package space.grayt.teremok.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.domain.VoicePart;
import space.grayt.teremok.storage.VoicingRepository;

/** Turns a text work and a cast into playback steps: audio where it exists, text elsewhere. */
public final class PlaybackService {

    private final VoicingRepository repository;

    public PlaybackService(VoicingRepository repository) {
        this.repository = repository;
    }

    public List<PlaybackStep> plan(TextWork textWork, Cast cast) {
        List<PlaybackStep> steps = new ArrayList<>(textWork.fragments().size());
        for (TextWorkFragment fragment : textWork.fragments()) {
            String voicePartName = textWork.voicePart(fragment.voicePartId())
                    .map(VoicePart::name)
                    .orElse(fragment.voicePartId());
            Optional<Voicing> voicing = cast.voicingFor(fragment.voicePartId()).flatMap(repository::find);
            Path audio = voicing
                    .map(found -> repository.audioFile(found.id(), fragment.number()))
                    .filter(PlaybackService::isPlayable)
                    .orElse(null);
            steps.add(new PlaybackStep(fragment, voicePartName,
                    audio == null ? null : voicing.map(Voicing::authorId).orElse(null), audio));
        }
        return steps;
    }

    /** An empty or missing file counts as no recording. */
    private static boolean isPlayable(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Pause long enough to read an unvoiced fragment. */
    public static Duration readingPause(String text) {
        long millis = Math.max(1200L, 60L * text.length());
        return Duration.ofMillis(Math.min(millis, 8000L));
    }

    /** First recorded fragment of a voicing, used by the play-sample command. */
    public Optional<Path> sample(Voicing voicing) {
        return voicing.recordedFragments().stream()
                .min(Comparator.naturalOrder())
                .map(fragmentNumber -> repository.audioFile(voicing.id(), fragmentNumber))
                .filter(PlaybackService::isPlayable);
    }
}
