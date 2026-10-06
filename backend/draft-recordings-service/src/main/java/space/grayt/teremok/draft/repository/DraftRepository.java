package space.grayt.teremok.draft.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import space.grayt.teremok.draft.domain.DraftRecording;
import space.grayt.teremok.draft.domain.RoleFragment;
import space.grayt.teremok.draft.domain.RoleProjection;
import space.grayt.teremok.events.TextWorkAddedEvent;

public interface DraftRepository {

    void saveTextWork(TextWorkAddedEvent event);

    void saveUser(UUID userId);

    boolean userExists(UUID userId);

    boolean fragmentExists(UUID fragmentId);

    Optional<RoleProjection> findRole(UUID roleId);

    List<RoleFragment> findRoleFragments(UUID roleId);

    void saveDraft(DraftRecording recording);

    Optional<DraftRecording> findDraft(UUID recordingId);

    List<DraftRecording> findUserRoleDrafts(UUID userId, UUID roleId);

    void deleteDrafts(List<UUID> recordingIds);

    void savePublication(UUID id, UUID userId, UUID textWorkId, UUID roleId, Instant publishedAt);
}
