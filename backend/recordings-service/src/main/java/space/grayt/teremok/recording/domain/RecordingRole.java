package space.grayt.teremok.recording.domain;

import java.util.UUID;

public record RecordingRole(UUID id, UUID textWorkId, String name) {
}
