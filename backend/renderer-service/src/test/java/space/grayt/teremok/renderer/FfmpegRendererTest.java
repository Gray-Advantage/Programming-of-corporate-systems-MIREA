package space.grayt.teremok.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.grayt.teremok.events.RenderRequestedEvent;
import space.grayt.teremok.storage.ObjectStorage;

class FfmpegRendererTest {

    @TempDir
    Path tempDirectory;

    @Test
    void rendersRealMp3FromFragmentAudio() throws Exception {
        assumeTrue(commandSucceeds(List.of("ffmpeg", "-version")), "FFmpeg is not installed");
        var first = tempDirectory.resolve("first.wav");
        var second = tempDirectory.resolve("second.wav");
        createTone(first, 440);
        createTone(second, 660);

        var storage = new FileObjectStorage(Map.of("first", first, "second", second), tempDirectory);
        var renderer = new FfmpegRenderer(storage, "ffmpeg");
        var renderId = UUID.randomUUID();
        var request = new RenderRequestedEvent.RenderRequestPayload(
                renderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                RenderRequestedEvent.OutputMode.SINGLE_FILE,
                List.of(new RenderRequestedEvent.SegmentAudioPayload(
                        UUID.randomUUID(),
                        1,
                        List.of(
                                new RenderRequestedEvent.FragmentAudioPayload(UUID.randomUUID(), 1, "first"),
                                new RenderRequestedEvent.FragmentAudioPayload(UUID.randomUUID(), 2, "second")))));

        var outputs = renderer.render(request);

        assertThat(outputs).singleElement().satisfies(output -> {
            assertThat(output.objectKey()).isEqualTo("renders/" + renderId + "/full.mp3");
            assertThat(output.contentType()).isEqualTo("audio/mpeg");
        });
        var rendered = storage.uploads.get("renders/" + renderId + "/full.mp3");
        assertThat(rendered).exists().isNotEmptyFile();
    }

    private static void createTone(Path output, int frequency) throws IOException, InterruptedException {
        var command = List.of(
                "ffmpeg", "-y", "-f", "lavfi", "-i",
                "sine=frequency=" + frequency + ":duration=0.1",
                "-c:a", "pcm_s16le", output.toString());
        if (!commandSucceeds(command)) {
            throw new IOException("Could not generate FFmpeg test input");
        }
    }

    private static boolean commandSucceeds(List<String> command) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        return process.waitFor() == 0;
    }

    private static final class FileObjectStorage implements ObjectStorage {

        private final Map<String, Path> sources;
        private final Path outputDirectory;
        private final Map<String, Path> uploads = new HashMap<>();

        private FileObjectStorage(Map<String, Path> sources, Path outputDirectory) {
            this.sources = sources;
            this.outputDirectory = outputDirectory;
        }

        @Override
        public void put(String objectKey, InputStream input, long size, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void copy(String sourceKey, String targetKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void download(String objectKey, Path target) {
            try {
                Files.copy(sources.get(objectKey), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void upload(Path source, String objectKey, String contentType) {
            try {
                var target = outputDirectory.resolve(UUID.randomUUID() + ".mp3");
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                uploads.put(objectKey, target);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
