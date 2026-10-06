package space.grayt.teremok.client.contract;

import java.util.List;
import java.util.UUID;

public record CreateRenderRequest(
        UUID textWorkId,
        OutputMode outputMode,
        List<RoleSelection> roles) {

    public CreateRenderRequest {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public enum OutputMode {
        SINGLE_FILE,
        BY_SEGMENTS
    }

    public record RoleSelection(
            UUID roleId,
            UUID roleRecordingId) {
    }
}
