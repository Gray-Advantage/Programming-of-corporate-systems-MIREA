package space.grayt.teremok.recording.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.grayt.teremok.client.contract.CreateRenderRequest;
import space.grayt.teremok.client.contract.PublishedRolesResponse;
import space.grayt.teremok.client.contract.RenderJobResponse;
import space.grayt.teremok.recording.service.RecordingService;

@RestController
@RequestMapping("/api/v1/recordings")
public class RecordingController {

    private final RecordingService service;
    private final space.grayt.teremok.storage.ObjectStorage storage;

    public RecordingController(RecordingService service, space.grayt.teremok.storage.ObjectStorage storage) {
        this.service = service;
        this.storage = storage;
    }

    @GetMapping("/renders/{renderId}/outputs/{index}")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadOutput(
            Authentication authentication, @PathVariable UUID renderId, @PathVariable int index) {
        var job = service.findRender(userId(authentication), renderId);
        if (!"COMPLETED".equals(job.status()) || index < 0 || index >= job.outputs().size()) {
            throw new space.grayt.teremok.recording.service.RecordingNotFoundException("Render output is not ready");
        }
        var output = job.outputs().get(index);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"recording-" + (index + 1) + ".mp3\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(output.contentType()))
                .body(new org.springframework.core.io.InputStreamResource(storage.open(output.objectKey())));
    }

    @GetMapping("/text-works/{textWorkId}/roles")
    public PublishedRolesResponse findPublishedRoles(@PathVariable UUID textWorkId) {
        return service.findPublishedRoles(textWorkId);
    }

    @PostMapping("/renders")
    public ResponseEntity<RenderJobResponse> createRender(
            Authentication authentication,
            @RequestBody CreateRenderRequest request) {
        return ResponseEntity.accepted().body(service.createRender(userId(authentication), request));
    }

    @GetMapping("/renders/{renderId}")
    public RenderJobResponse findRender(Authentication authentication, @PathVariable UUID renderId) {
        return service.findRender(userId(authentication), renderId);
    }

    private static UUID userId(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
