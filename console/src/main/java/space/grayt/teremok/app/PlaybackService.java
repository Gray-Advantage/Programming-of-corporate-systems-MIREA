package space.grayt.teremok.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import space.grayt.teremok.domain.Book;
import space.grayt.teremok.domain.Cast;
import space.grayt.teremok.domain.Line;
import space.grayt.teremok.domain.Speaker;
import space.grayt.teremok.domain.Voicing;
import space.grayt.teremok.storage.VoicingRepository;

/** Превращает книгу и каст в последовательность шагов: где звук, а где чтение текстом. */
public final class PlaybackService {

    private final VoicingRepository repository;

    public PlaybackService(VoicingRepository repository) {
        this.repository = repository;
    }

    public List<PlaybackStep> plan(Book book, Cast cast) {
        List<PlaybackStep> steps = new ArrayList<>(book.lines().size());
        for (Line line : book.lines()) {
            String speakerName = book.speaker(line.speakerId()).map(Speaker::name).orElse(line.speakerId());
            Optional<Voicing> voicing = cast.voicingFor(line.speakerId()).flatMap(repository::find);
            Path audio = voicing
                    .map(found -> repository.audioFile(found.id(), line.number()))
                    .filter(PlaybackService::isPlayable)
                    .orElse(null);
            steps.add(new PlaybackStep(line, speakerName,
                    audio == null ? null : voicing.map(Voicing::authorId).orElse(null), audio));
        }
        return steps;
    }

    /** Пустой или пропавший файл равнозначен отсутствию озвучки. */
    private static boolean isPlayable(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Пауза, чтобы успеть прочитать неозвученную реплику. */
    public static Duration readingPause(String text) {
        long millis = Math.max(1200L, 60L * text.length());
        return Duration.ofMillis(Math.min(millis, 8000L));
    }

    /** Первая записанная реплика роли — для кнопки «прослушать пример». */
    public Optional<Path> sample(Voicing voicing) {
        return voicing.recordedLines().stream()
                .min(Comparator.naturalOrder())
                .map(lineNumber -> repository.audioFile(voicing.id(), lineNumber))
                .filter(PlaybackService::isPlayable);
    }
}
