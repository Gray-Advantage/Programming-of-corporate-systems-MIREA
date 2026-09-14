package space.grayt.teremok.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A bundled book: title and lines in reading order. */
public record Book(String id, String title, List<Line> lines, Map<String, Speaker> speakerById) {

    public Book {
        lines = List.copyOf(lines);
        speakerById = Map.copyOf(speakerById);
    }

    /** Speakers in order of first appearance. */
    public List<Speaker> speakers() {
        Map<String, Speaker> ordered = new LinkedHashMap<>();
        for (Line line : lines) {
            ordered.computeIfAbsent(line.speakerId(), speakerById::get);
        }
        return new ArrayList<>(ordered.values());
    }

    public List<Line> linesOf(String speakerId) {
        return lines.stream().filter(line -> line.speakerId().equals(speakerId)).toList();
    }

    public Optional<Speaker> speaker(String speakerId) {
        return Optional.ofNullable(speakerById.get(speakerId));
    }
}
