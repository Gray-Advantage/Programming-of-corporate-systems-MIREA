package space.grayt.teremok.recording.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import space.grayt.teremok.client.contract.CreateRenderRequest;
import space.grayt.teremok.events.RenderRequestedEvent;
import space.grayt.teremok.recording.domain.PublishedRoleRecording;
import space.grayt.teremok.recording.domain.RecordingRole;
import space.grayt.teremok.recording.domain.RenderFragment;
import space.grayt.teremok.recording.messaging.RenderRequestPublisher;
import space.grayt.teremok.recording.repository.RecordingRepository;

class RecordingServiceTest {

    @Test
    void createsSegmentRenderWithFragmentsInTextOrder() {
        var repository = mock(RecordingRepository.class);
        var publisher = mock(RenderRequestPublisher.class);
        var now = Instant.parse("2026-10-06T12:00:00Z");
        var service = new RecordingService(repository, publisher, Clock.fixed(now, ZoneOffset.UTC));

        var userId = UUID.randomUUID();
        var workId = UUID.randomUUID();
        var roleA = UUID.randomUUID();
        var roleB = UUID.randomUUID();
        var recordingA = published(userId, workId, roleA);
        var recordingB = published(userId, workId, roleB);
        var segment = UUID.randomUUID();
        var fragmentA = UUID.randomUUID();
        var fragmentB = UUID.randomUUID();

        when(repository.userExists(userId)).thenReturn(true);
        when(repository.findRoles(workId)).thenReturn(List.of(
                new RecordingRole(roleA, workId, "A"),
                new RecordingRole(roleB, workId, "B")));
        when(repository.findRoleRecording(recordingA.id())).thenReturn(Optional.of(recordingA));
        when(repository.findRoleRecording(recordingB.id())).thenReturn(Optional.of(recordingB));
        when(repository.findRenderFragments(recordingA.id()))
                .thenReturn(List.of(new RenderFragment(segment, 0, fragmentA, 1, "published/a.wav")));
        when(repository.findRenderFragments(recordingB.id()))
                .thenReturn(List.of(new RenderFragment(segment, 0, fragmentB, 0, "published/b.wav")));
        when(repository.countTextWorkFragments(workId)).thenReturn(2L);

        var response = service.createRender(userId, new CreateRenderRequest(
                workId,
                CreateRenderRequest.OutputMode.BY_SEGMENTS,
                List.of(
                        new CreateRenderRequest.RoleSelection(roleA, recordingA.id()),
                        new CreateRenderRequest.RoleSelection(roleB, recordingB.id()))));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.createdAt()).isEqualTo(now);
        var eventCaptor = ArgumentCaptor.forClass(RenderRequestedEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        var payload = eventCaptor.getValue().renderRequest();
        assertThat(payload.outputMode()).isEqualTo(RenderRequestedEvent.OutputMode.BY_SEGMENTS);
        assertThat(payload.segments()).hasSize(1);
        assertThat(payload.segments().getFirst().fragments())
                .extracting(RenderRequestedEvent.FragmentAudioPayload::objectKey)
                .containsExactly("published/b.wav", "published/a.wav");
    }

    private static PublishedRoleRecording published(UUID userId, UUID workId, UUID roleId) {
        return new PublishedRoleRecording(
                UUID.randomUUID(), userId, workId, roleId, Instant.parse("2026-10-06T11:00:00Z"));
    }
}
