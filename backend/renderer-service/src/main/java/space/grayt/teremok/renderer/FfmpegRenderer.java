package space.grayt.teremok.renderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import space.grayt.teremok.events.RenderCompletedEvent;
import space.grayt.teremok.events.RenderRequestedEvent;
import space.grayt.teremok.storage.ObjectStorage;

@Service
public class FfmpegRenderer {

    private static final String OUTPUT_CONTENT_TYPE = "audio/mpeg";

    private final ObjectStorage storage;
    private final String ffmpegPath;

    public FfmpegRenderer(ObjectStorage storage, @Value("${renderer.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        this.storage = storage;
        this.ffmpegPath = ffmpegPath;
    }

    public List<RenderCompletedEvent.OutputPayload> render(RenderRequestedEvent.RenderRequestPayload request)
            throws IOException, InterruptedException {
        var workDirectory = Files.createTempDirectory("teremok-render-" + request.id() + "-");
        try {
            return switch (request.outputMode()) {
                case SINGLE_FILE -> List.of(renderSingleFile(request, workDirectory));
                case BY_SEGMENTS -> renderSegments(request, workDirectory);
            };
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private RenderCompletedEvent.OutputPayload renderSingleFile(
            RenderRequestedEvent.RenderRequestPayload request,
            Path workDirectory) throws IOException, InterruptedException {
        var fragments = request.segments().stream()
                .sorted(Comparator.comparingInt(RenderRequestedEvent.SegmentAudioPayload::orderInTextWork))
                .flatMap(segment -> segment.fragments().stream()
                        .sorted(Comparator.comparingInt(RenderRequestedEvent.FragmentAudioPayload::orderInSegment)))
                .toList();
        var output = renderFragments(fragments, workDirectory.resolve("single"));
        var key = "renders/%s/full.mp3".formatted(request.id());
        storage.upload(output, key, OUTPUT_CONTENT_TYPE);
        return new RenderCompletedEvent.OutputPayload(null, key, OUTPUT_CONTENT_TYPE);
    }

    private List<RenderCompletedEvent.OutputPayload> renderSegments(
            RenderRequestedEvent.RenderRequestPayload request,
            Path workDirectory) throws IOException, InterruptedException {
        var outputs = new ArrayList<RenderCompletedEvent.OutputPayload>();
        for (var segment : request.segments().stream()
                .sorted(Comparator.comparingInt(RenderRequestedEvent.SegmentAudioPayload::orderInTextWork))
                .toList()) {
            var output = renderFragments(
                    segment.fragments().stream()
                            .sorted(Comparator.comparingInt(RenderRequestedEvent.FragmentAudioPayload::orderInSegment))
                            .toList(),
                    workDirectory.resolve("segment-" + segment.orderInTextWork()));
            var key = "renders/%s/segments/%04d-%s.mp3".formatted(
                    request.id(), segment.orderInTextWork(), segment.id());
            storage.upload(output, key, OUTPUT_CONTENT_TYPE);
            outputs.add(new RenderCompletedEvent.OutputPayload(segment.id(), key, OUTPUT_CONTENT_TYPE));
        }
        return outputs;
    }

    private Path renderFragments(
            List<RenderRequestedEvent.FragmentAudioPayload> fragments,
            Path directory) throws IOException, InterruptedException {
        if (fragments.isEmpty()) {
            throw new IllegalArgumentException("Cannot render an empty fragment list");
        }
        Files.createDirectories(directory);
        var normalized = new ArrayList<Path>();
        for (var index = 0; index < fragments.size(); index++) {
            var fragment = fragments.get(index);
            var source = directory.resolve("source-%05d".formatted(index));
            var wav = directory.resolve("normalized-%05d.wav".formatted(index));
            storage.download(fragment.objectKey(), source);
            runFfmpeg(List.of(
                    ffmpegPath,
                    "-y",
                    "-i", source.toString(),
                    "-vn",
                    "-ac", "2",
                    "-ar", "44100",
                    "-c:a", "pcm_s16le",
                    wav.toString()));
            normalized.add(wav);
        }

        var concatFile = directory.resolve("concat.txt");
        var lines = normalized.stream()
                .map(path -> "file '" + path.toAbsolutePath().toString().replace('\\', '/').replace("'", "'\\''") + "'")
                .toList();
        Files.write(concatFile, lines, StandardCharsets.UTF_8);
        var output = directory.resolve("output.mp3");
        runFfmpeg(List.of(
                ffmpegPath,
                "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", concatFile.toString(),
                "-vn",
                "-c:a", "libmp3lame",
                "-q:a", "2",
                output.toString()));
        return output;
    }

    private void runFfmpeg(List<String> command) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("FFmpeg exited with code " + exitCode + ": " + truncate(output));
        }
    }

    private static String truncate(String value) {
        return value.length() <= 2_000 ? value : value.substring(value.length() - 2_000);
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary files are best-effort cleanup only.
                }
            });
        } catch (IOException ignored) {
            // Temporary files are best-effort cleanup only.
        }
    }
}
