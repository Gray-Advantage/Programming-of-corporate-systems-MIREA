package space.grayt.teremok.recording.domain;

import java.util.UUID;

public record RenderFragment(
        UUID segmentId,
        int segmentOrder,
        UUID fragmentId,
        int fragmentOrder,
        String objectKey) {
}
