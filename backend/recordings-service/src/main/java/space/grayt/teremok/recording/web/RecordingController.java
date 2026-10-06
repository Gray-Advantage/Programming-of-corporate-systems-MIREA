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

    public RecordingController(RecordingService service) {
        this.service = service;
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
