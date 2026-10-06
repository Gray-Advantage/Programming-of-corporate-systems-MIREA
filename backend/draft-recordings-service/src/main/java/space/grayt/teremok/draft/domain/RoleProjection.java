package space.grayt.teremok.draft.domain;

import java.util.UUID;

public record RoleProjection(
        UUID id,
        UUID textWorkId,
        String name) {
}
