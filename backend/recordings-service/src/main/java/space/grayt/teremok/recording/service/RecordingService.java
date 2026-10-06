package space.grayt.teremok.recording.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.client.contract.CreateRenderRequest;
import space.grayt.teremok.client.contract.PublishedRolesResponse;
import space.grayt.teremok.client.contract.RenderJobResponse;
import space.grayt.teremok.events.RenderRequestedEvent;
import space.grayt.teremok.recording.domain.RenderFragment;
import space.grayt.teremok.recording.domain.RenderJob;
import space.grayt.teremok.recording.messaging.RenderRequestPublisher;
import space.grayt.teremok.recording.repository.RecordingRepository;

@Service
public class RecordingService {

    private final RecordingRepository repository;
    private final RenderRequestPublisher publisher;
    private final Clock clock;

    public RecordingService(RecordingRepository repository, RenderRequestPublisher publisher, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    public PublishedRolesResponse findPublishedRoles(UUID textWorkId) {
        var roles = repository.findRoles(textWorkId);
        if (roles.isEmpty()) {
            throw new RecordingNotFoundException("Text work not found or has no roles: " + textWorkId);
        }
        return new PublishedRolesResponse(
                textWorkId,
                roles.stream()
                        .map(role -> new PublishedRolesResponse.RoleRecordings(
                                role.id(),
                                role.name(),
                                repository.findRoleRecordings(role.id()).stream()
                                        .map(recording -> new PublishedRolesResponse.PublishedRecording(
                                                recording.id(), recording.userId(), recording.publishedAt()))
                                        .toList()))
                        .toList());
    }

    @Transactional
    public RenderJobResponse createRender(UUID userId, CreateRenderRequest request) {
        if (!repository.userExists(userId)) {
            throw new RecordingNotFoundException("User projection not found: " + userId);
        }
        if (request.textWorkId() == null || request.outputMode() == null) {
            throw new RecordingValidationException("textWorkId and outputMode are required");
        }
        var roles = repository.findRoles(request.textWorkId());
        if (roles.isEmpty()) {
            throw new RecordingNotFoundException("Text work not found or has no roles: " + request.textWorkId());
        }
        var selections = new HashMap<UUID, UUID>();
        for (var selection : request.roles()) {
            if (selection.roleId() == null || selection.roleRecordingId() == null) {
                throw new RecordingValidationException("Every role selection must contain both ids");
            }
            if (selections.put(selection.roleId(), selection.roleRecordingId()) != null) {
                throw new RecordingValidationException("A role can be selected only once");
            }
        }
        var requiredRoleIds = roles.stream().map(role -> role.id()).collect(Collectors.toSet());
        if (!selections.keySet().equals(requiredRoleIds)) {
            throw new RecordingValidationException("Exactly one published recording must be selected for every role");
        }

        var audioFragments = new ArrayList<RenderFragment>();
        for (var selection : selections.entrySet()) {
            var recording = repository.findRoleRecording(selection.getValue())
                    .orElseThrow(() -> new RecordingNotFoundException(
                            "Published role recording not found: " + selection.getValue()));
            if (!recording.roleId().equals(selection.getKey())
                    || !recording.textWorkId().equals(request.textWorkId())) {
                throw new RecordingValidationException(
                        "Published recording does not belong to the selected role and text work: " + recording.id());
            }
            audioFragments.addAll(repository.findRenderFragments(recording.id()));
        }
        var uniqueFragmentIds = audioFragments.stream().map(RenderFragment::fragmentId).collect(Collectors.toSet());
        if (uniqueFragmentIds.size() != repository.countTextWorkFragments(request.textWorkId())) {
            throw new RecordingValidationException("Selected role recordings do not cover every text fragment");
        }

        var segments = toSegments(audioFragments);
        var renderId = UUID.randomUUID();
        var createdAt = Instant.now(clock);
        var job = new RenderJob(
                renderId,
                userId,
                request.textWorkId(),
                request.outputMode().name(),
                "PENDING",
                createdAt,
                null,
                null);
        repository.saveRenderJob(job, selections);
        publisher.publish(new RenderRequestedEvent(
                UUID.randomUUID(),
                1,
                createdAt,
                new RenderRequestedEvent.RenderRequestPayload(
                        renderId,
                        userId,
                        request.textWorkId(),
                        RenderRequestedEvent.OutputMode.valueOf(request.outputMode().name()),
                        segments)));
        return toResponse(job, List.of());
    }

    public RenderJobResponse findRender(UUID userId, UUID renderId) {
        var job = repository.findRenderJob(renderId, userId)
                .orElseThrow(() -> new RecordingNotFoundException("Render job not found: " + renderId));
        return toResponse(job, repository.findRenderOutputs(renderId));
    }

    private static List<RenderRequestedEvent.SegmentAudioPayload> toSegments(List<RenderFragment> fragments) {
        var bySegment = new LinkedHashMap<UUID, List<RenderFragment>>();
        fragments.stream()
                .sorted(Comparator.comparingInt(RenderFragment::segmentOrder)
                        .thenComparingInt(RenderFragment::fragmentOrder))
                .forEach(fragment -> bySegment.computeIfAbsent(fragment.segmentId(), ignored -> new ArrayList<>())
                        .add(fragment));
        return bySegment.values().stream()
                .map(segmentFragments -> {
                    var first = segmentFragments.getFirst();
                    return new RenderRequestedEvent.SegmentAudioPayload(
                            first.segmentId(),
                            first.segmentOrder(),
                            segmentFragments.stream()
                                    .map(fragment -> new RenderRequestedEvent.FragmentAudioPayload(
                                            fragment.fragmentId(),
                                            fragment.fragmentOrder(),
                                            fragment.objectKey()))
                                    .toList());
                })
                .toList();
    }

    private static RenderJobResponse toResponse(RenderJob job, List<space.grayt.teremok.recording.domain.RenderOutput> outputs) {
        return new RenderJobResponse(
                job.id(),
                job.textWorkId(),
                job.outputMode(),
                job.status(),
                job.createdAt(),
                job.completedAt(),
                job.error(),
                outputs.stream()
                        .map(output -> new RenderJobResponse.RenderOutput(
                                output.segmentId(), output.objectKey(), output.contentType()))
                        .toList());
    }
}
