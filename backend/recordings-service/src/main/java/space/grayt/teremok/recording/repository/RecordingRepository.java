package space.grayt.teremok.recording.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import space.grayt.teremok.events.RenderCompletedEvent;
import space.grayt.teremok.events.RoleRecordingPublishedEvent;
import space.grayt.teremok.events.TextWorkAddedEvent;
import space.grayt.teremok.recording.domain.PublishedRoleRecording;
import space.grayt.teremok.recording.domain.RecordingRole;
import space.grayt.teremok.recording.domain.RenderFragment;
import space.grayt.teremok.recording.domain.RenderJob;
import space.grayt.teremok.recording.domain.RenderOutput;

public interface RecordingRepository {

    void saveTextWork(TextWorkAddedEvent event);

    void saveUser(UUID userId);

    void savePublishedRole(RoleRecordingPublishedEvent event);

    boolean userExists(UUID userId);

    List<RecordingRole> findRoles(UUID textWorkId);

    List<PublishedRoleRecording> findRoleRecordings(UUID roleId);

    Optional<PublishedRoleRecording> findRoleRecording(UUID id);

    List<RenderFragment> findRenderFragments(UUID roleRecordingId);

    long countTextWorkFragments(UUID textWorkId);

    void saveRenderJob(RenderJob job, Map<UUID, UUID> roleSelections);

    Optional<RenderJob> findRenderJob(UUID id, UUID userId);

    List<RenderOutput> findRenderOutputs(UUID renderJobId);

    void completeRender(RenderCompletedEvent event, Instant completedAt);
}
