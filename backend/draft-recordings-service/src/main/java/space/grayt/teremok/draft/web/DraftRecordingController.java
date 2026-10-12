package space.grayt.teremok.draft.web;

import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import space.grayt.teremok.client.contract.DraftRecordingResponse;
import space.grayt.teremok.client.contract.PublishRoleRecordingRequest;
import space.grayt.teremok.client.contract.PublishedRoleRecordingResponse;
import space.grayt.teremok.client.contract.RoleDraftsResponse;
import space.grayt.teremok.draft.service.DraftRecordingService;

@RestController
@RequestMapping("/api/v1/draft-recordings")
public class DraftRecordingController {

    private final DraftRecordingService service;
    private final space.grayt.teremok.storage.ObjectStorage storage;

    public DraftRecordingController(DraftRecordingService service, space.grayt.teremok.storage.ObjectStorage storage) {
        this.service = service;
        this.storage = storage;
    }

    @GetMapping("/{recordingId}/audio")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> download(
            Authentication authentication, @PathVariable UUID recordingId) {
        var draft = service.ownedDraft(userId(authentication), recordingId);
        return ResponseEntity.ok()
                .header("Content-Disposition", org.springframework.http.ContentDisposition.attachment()
                        .filename(draft.originalFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(draft.sizeBytes())
                .body(new org.springframework.core.io.InputStreamResource(storage.open(draft.objectKey())));
    }

    @PostMapping("/fragments/{fragmentId}")
    public ResponseEntity<DraftRecordingResponse> upload(
            Authentication authentication,
            @PathVariable UUID fragmentId,
            @RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            var result = service.upload(
                    userId(authentication),
                    fragmentId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    input);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
    }

    @GetMapping("/roles/{roleId}")
    public RoleDraftsResponse findRoleDrafts(Authentication authentication, @PathVariable UUID roleId) {
        return service.findRoleDrafts(userId(authentication), roleId);
    }

    @DeleteMapping("/{recordingId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID recordingId) {
        service.delete(userId(authentication), recordingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/roles/{roleId}/publish")
    public ResponseEntity<PublishedRoleRecordingResponse> publish(
            Authentication authentication,
            @PathVariable UUID roleId,
            @RequestBody PublishRoleRecordingRequest request) {
        return ResponseEntity.accepted().body(service.publish(userId(authentication), roleId, request));
    }

    private static UUID userId(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
