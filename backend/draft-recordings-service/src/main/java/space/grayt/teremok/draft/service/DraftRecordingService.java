package space.grayt.teremok.draft.service;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.grayt.teremok.client.contract.DraftRecordingResponse;
import space.grayt.teremok.client.contract.PublishRoleRecordingRequest;
import space.grayt.teremok.client.contract.PublishedRoleRecordingResponse;
import space.grayt.teremok.client.contract.RoleDraftsResponse;
import space.grayt.teremok.draft.domain.DraftRecording;
import space.grayt.teremok.draft.messaging.RoleRecordingPublisher;
import space.grayt.teremok.draft.repository.DraftRepository;
import space.grayt.teremok.events.RoleRecordingPublishedEvent;
import space.grayt.teremok.storage.ObjectStorage;

@Service
public class DraftRecordingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DraftRecordingService.class);

    private final DraftRepository repository;
    private final ObjectStorage storage;
    private final RoleRecordingPublisher publisher;
    private final Clock clock;

    public DraftRecordingService(
            DraftRepository repository,
            ObjectStorage storage,
            RoleRecordingPublisher publisher,
            Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public DraftRecordingResponse upload(
            UUID userId,
            UUID fragmentId,
            String originalFileName,
            String contentType,
            long size,
            InputStream input) {
        requireKnownUser(userId);
        if (!repository.fragmentExists(fragmentId)) {
            throw new DraftNotFoundException("Fragment not found: " + fragmentId);
        }
        if (size <= 0) {
            throw new DraftValidationException("Audio file must not be empty");
        }
        var id = UUID.randomUUID();
        var safeName = safeFileName(originalFileName);
        var actualContentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
        var objectKey = "draft/%s/%s/%s/%s".formatted(userId, fragmentId, id, safeName);
        var recording = new DraftRecording(
                id, userId, fragmentId, objectKey, safeName, actualContentType, size, Instant.now(clock));
        try {
            storage.put(objectKey, input, size, actualContentType);
            repository.saveDraft(recording);
        } catch (RuntimeException exception) {
            try {
                storage.delete(objectKey);
            } catch (RuntimeException ignored) {
                exception.addSuppressed(ignored);
            }
            throw exception;
        }
        return toResponse(recording);
    }

    public RoleDraftsResponse findRoleDrafts(UUID userId, UUID roleId) {
        requireKnownUser(userId);
        var role = repository.findRole(roleId)
                .orElseThrow(() -> new DraftNotFoundException("Role not found: " + roleId));
        var draftsByFragment = repository.findUserRoleDrafts(userId, roleId).stream()
                .collect(Collectors.groupingBy(DraftRecording::fragmentId));
        var fragments = repository.findRoleFragments(roleId).stream()
                .map(fragment -> new RoleDraftsResponse.FragmentDrafts(
                        fragment.id(),
                        fragment.segmentId(),
                        fragment.segmentOrder(),
                        fragment.fragmentOrder(),
                        draftsByFragment.getOrDefault(fragment.id(), List.of()).stream()
                                .map(DraftRecordingService::toResponse)
                                .toList()))
                .toList();
        return new RoleDraftsResponse(role.id(), role.textWorkId(), fragments);
    }

    @Transactional
    public void delete(UUID userId, UUID recordingId) {
        var recording = ownedDraft(userId, recordingId);
        storage.delete(recording.objectKey());
        repository.deleteDrafts(List.of(recordingId));
    }

    @Transactional
    public PublishedRoleRecordingResponse publish(
            UUID userId,
            UUID roleId,
            PublishRoleRecordingRequest request) {
        requireKnownUser(userId);
        var role = repository.findRole(roleId)
                .orElseThrow(() -> new DraftNotFoundException("Role not found: " + roleId));
        var requiredFragments = repository.findRoleFragments(roleId);
        if (requiredFragments.isEmpty()) {
            throw new DraftValidationException("Role has no fragments");
        }

        var selected = new HashMap<UUID, UUID>();
        for (var selection : request.selections()) {
            if (selection.fragmentId() == null || selection.draftRecordingId() == null) {
                throw new DraftValidationException("Every selection must contain fragmentId and draftRecordingId");
            }
            if (selected.put(selection.fragmentId(), selection.draftRecordingId()) != null) {
                throw new DraftValidationException("A fragment can be selected only once");
            }
        }
        var requiredIds = requiredFragments.stream().map(fragment -> fragment.id()).collect(Collectors.toSet());
        if (!selected.keySet().equals(requiredIds)) {
            throw new DraftValidationException("Exactly one draft must be selected for every fragment of the role");
        }

        var roleRecordingId = UUID.randomUUID();
        var publishedAt = Instant.now(clock);
        var selectedDrafts = new ArrayList<DraftRecording>();
        var publishedFragments = new ArrayList<RoleRecordingPublishedEvent.FragmentRecordingPayload>();
        var copiedKeys = new ArrayList<String>();
        var eventPublished = false;
        try {
            for (var fragment : requiredFragments) {
                var draft = ownedDraft(userId, selected.get(fragment.id()));
                if (!draft.fragmentId().equals(fragment.id())) {
                    throw new DraftValidationException("Selected draft belongs to another fragment: " + draft.id());
                }
                var publishedId = UUID.randomUUID();
                var targetKey = "published/roles/%s/%s/%s/%s".formatted(
                        roleRecordingId, fragment.id(), publishedId, draft.originalFileName());
                storage.copy(draft.objectKey(), targetKey);
                copiedKeys.add(targetKey);
                selectedDrafts.add(draft);
                publishedFragments.add(new RoleRecordingPublishedEvent.FragmentRecordingPayload(
                        publishedId,
                        fragment.id(),
                        targetKey,
                        draft.contentType(),
                        draft.sizeBytes()));
            }

            repository.savePublication(roleRecordingId, userId, role.textWorkId(), roleId, publishedAt);
            var event = new RoleRecordingPublishedEvent(
                    UUID.randomUUID(),
                    1,
                    publishedAt,
                    new RoleRecordingPublishedEvent.RoleRecordingPayload(
                            roleRecordingId,
                            userId,
                            role.textWorkId(),
                            roleId,
                            publishedAt,
                            publishedFragments));
            publisher.publish(event);
            eventPublished = true;
            repository.deleteDrafts(selectedDrafts.stream().map(DraftRecording::id).toList());
            for (var draft : selectedDrafts) {
                try {
                    storage.delete(draft.objectKey());
                } catch (RuntimeException cleanupException) {
                    LOGGER.warn("Published draft object could not be removed: {}", draft.objectKey(), cleanupException);
                }
            }
            return new PublishedRoleRecordingResponse(roleRecordingId, publishedAt);
        } catch (RuntimeException exception) {
            if (!eventPublished) {
                for (var key : copiedKeys) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException cleanupException) {
                        exception.addSuppressed(cleanupException);
                    }
                }
            }
            throw exception;
        }
    }

    private DraftRecording ownedDraft(UUID userId, UUID recordingId) {
        var recording = repository.findDraft(recordingId)
                .orElseThrow(() -> new DraftNotFoundException("Draft recording not found: " + recordingId));
        if (!recording.userId().equals(userId)) {
            throw new DraftNotFoundException("Draft recording not found: " + recordingId);
        }
        return recording;
    }

    private void requireKnownUser(UUID userId) {
        if (!repository.userExists(userId)) {
            throw new DraftNotFoundException("User projection not found: " + userId);
        }
    }

    private static String safeFileName(String fileName) {
        var actual = fileName == null || fileName.isBlank() ? "recording" : fileName;
        actual = actual.replace('\\', '/');
        actual = actual.substring(actual.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        return actual.isBlank() ? "recording" : actual;
    }

    private static DraftRecordingResponse toResponse(DraftRecording recording) {
        return new DraftRecordingResponse(
                recording.id(),
                recording.fragmentId(),
                recording.originalFileName(),
                recording.contentType(),
                recording.sizeBytes(),
                recording.createdAt());
    }
}
