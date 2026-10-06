package space.grayt.teremok.draft.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import space.grayt.teremok.client.contract.PublishRoleRecordingRequest;
import space.grayt.teremok.draft.domain.DraftRecording;
import space.grayt.teremok.draft.domain.RoleFragment;
import space.grayt.teremok.draft.domain.RoleProjection;
import space.grayt.teremok.draft.messaging.RoleRecordingPublisher;
import space.grayt.teremok.draft.repository.DraftRepository;
import space.grayt.teremok.events.RoleRecordingPublishedEvent;
import space.grayt.teremok.storage.ObjectStorage;

class DraftRecordingServiceTest {

    @Test
    void publishesExactlySelectedDraftsAndLeavesOtherVariantsUntouched() {
        var repository = mock(DraftRepository.class);
        var storage = mock(ObjectStorage.class);
        var publisher = mock(RoleRecordingPublisher.class);
        var now = Instant.parse("2026-10-06T12:00:00Z");
        var service = new DraftRecordingService(
                repository, storage, publisher, Clock.fixed(now, ZoneOffset.UTC));

        var userId = UUID.randomUUID();
        var workId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var segmentId = UUID.randomUUID();
        var fragmentA = UUID.randomUUID();
        var fragmentB = UUID.randomUUID();
        var selectedA = draft(userId, fragmentA, "draft/a.wav");
        var selectedB = draft(userId, fragmentB, "draft/b.wav");
        var unused = draft(userId, fragmentA, "draft/unused.wav");

        when(repository.userExists(userId)).thenReturn(true);
        when(repository.findRole(roleId)).thenReturn(Optional.of(new RoleProjection(roleId, workId, "Narrator")));
        when(repository.findRoleFragments(roleId)).thenReturn(List.of(
                new RoleFragment(fragmentA, segmentId, 0, 0),
                new RoleFragment(fragmentB, segmentId, 0, 1)));
        when(repository.findDraft(selectedA.id())).thenReturn(Optional.of(selectedA));
        when(repository.findDraft(selectedB.id())).thenReturn(Optional.of(selectedB));

        var response = service.publish(userId, roleId, new PublishRoleRecordingRequest(List.of(
                new PublishRoleRecordingRequest.Selection(fragmentA, selectedA.id()),
                new PublishRoleRecordingRequest.Selection(fragmentB, selectedB.id()))));

        assertThat(response.publishedAt()).isEqualTo(now);
        verify(repository).deleteDrafts(List.of(selectedA.id(), selectedB.id()));
        verify(storage).delete(selectedA.objectKey());
        verify(storage).delete(selectedB.objectKey());
        verify(storage, never()).delete(unused.objectKey());

        var eventCaptor = ArgumentCaptor.forClass(RoleRecordingPublishedEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().roleRecording().fragments())
                .extracting(RoleRecordingPublishedEvent.FragmentRecordingPayload::fragmentId)
                .containsExactly(fragmentA, fragmentB);
        assertThat(eventCaptor.getValue().roleRecording().fragments())
                .allSatisfy(fragment -> assertThat(fragment.objectKey()).startsWith("published/roles/"));
    }

    private static DraftRecording draft(UUID userId, UUID fragmentId, String key) {
        return new DraftRecording(
                UUID.randomUUID(),
                userId,
                fragmentId,
                key,
                "voice.wav",
                "audio/wav",
                42,
                Instant.parse("2026-10-06T11:00:00Z"));
    }
}
