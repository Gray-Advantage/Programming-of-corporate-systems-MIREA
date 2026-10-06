package space.grayt.teremok.client.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublishedRolesResponse(
        UUID textWorkId,
        List<RoleRecordings> roles) {

    public PublishedRolesResponse {
        roles = List.copyOf(roles);
    }

    public record RoleRecordings(
            UUID roleId,
            String roleName,
            List<PublishedRecording> recordings) {

        public RoleRecordings {
            recordings = List.copyOf(recordings);
        }
    }

    public record PublishedRecording(
            UUID id,
            UUID userId,
            Instant publishedAt) {
    }
}
