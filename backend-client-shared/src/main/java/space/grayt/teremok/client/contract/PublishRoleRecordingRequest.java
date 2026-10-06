package space.grayt.teremok.client.contract;

import java.util.List;
import java.util.UUID;

public record PublishRoleRecordingRequest(List<Selection> selections) {

    public PublishRoleRecordingRequest {
        selections = selections == null ? List.of() : List.copyOf(selections);
    }

    public record Selection(UUID fragmentId, UUID draftRecordingId) {
    }
}
