package space.grayt.teremok.draft.domain;

import java.util.UUID;

public record RoleFragment(
        UUID id,
        UUID segmentId,
        int segmentOrder,
        int fragmentOrder) {
}
