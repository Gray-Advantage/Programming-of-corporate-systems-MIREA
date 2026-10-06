package space.grayt.teremok.recording.domain;

import java.util.UUID;

public record RenderOutput(UUID segmentId, String objectKey, String contentType) {
}
