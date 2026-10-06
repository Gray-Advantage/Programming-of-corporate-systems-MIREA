package space.grayt.teremok.client.contract;

import java.util.List;
import java.util.UUID;

public record RoleDraftsResponse(
        UUID roleId,
        UUID textWorkId,
        List<FragmentDrafts> fragments) {

    public RoleDraftsResponse {
        fragments = List.copyOf(fragments);
    }

    public record FragmentDrafts(
            UUID fragmentId,
            UUID segmentId,
            int segmentOrder,
            int fragmentOrder,
            List<DraftRecordingResponse> recordings) {

        public FragmentDrafts {
            recordings = List.copyOf(recordings);
        }
    }
}
