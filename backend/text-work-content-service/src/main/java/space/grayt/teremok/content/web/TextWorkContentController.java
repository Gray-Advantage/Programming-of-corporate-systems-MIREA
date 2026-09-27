package space.grayt.teremok.content.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.grayt.teremok.client.contract.CountResponse;
import space.grayt.teremok.client.contract.TextWorkContentResponse;
import space.grayt.teremok.client.contract.TextWorkContentResponse.Segment;
import space.grayt.teremok.client.contract.TextWorkContentResponse.VoicePart;
import space.grayt.teremok.client.contract.TextWorkContentResponse.VoicePartFragment;
import space.grayt.teremok.content.domain.TextWorkContent;
import space.grayt.teremok.content.repository.TextWorkContentRepository;

@RestController
@RequestMapping("/api/v1/text-work-content/text-works")
public class TextWorkContentController {

    private final TextWorkContentRepository repository;

    public TextWorkContentController(TextWorkContentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TextWorkContentResponse> findById(@PathVariable UUID id) {
        return repository.findById(id)
                .map(TextWorkContentController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/count")
    public CountResponse count() {
        return new CountResponse(repository.count());
    }

    private static TextWorkContentResponse toResponse(TextWorkContent textWork) {
        var segments = textWork.segments().stream()
                .map(source -> new Segment(
                        source.id(),
                        source.orderInTextWork(),
                        source.name(),
                        source.fragments().stream()
                                .map(fragment -> new VoicePartFragment(
                                        fragment.id(),
                                        fragment.orderInSegment(),
                                        fragment.content(),
                                        fragment.voicePartId()))
                                .toList()))
                .toList();
        var voiceParts = textWork.voiceParts().stream()
                .map(source -> new VoicePart(source.id(), source.name(), source.totalFragmentsCount()))
                .toList();
        return new TextWorkContentResponse(textWork.id(), segments, voiceParts);
    }
}
