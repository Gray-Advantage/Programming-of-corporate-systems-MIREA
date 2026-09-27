package space.grayt.teremok.content.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.grayt.teremok.content.repository.TextWorkContentRepository;

@RestController
@RequestMapping("/api/v1/text-work-content/text-works")
public class TextWorkContentController {

    private final TextWorkContentRepository repository;

    public TextWorkContentController(TextWorkContentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/count")
    public CountResponse count() {
        return new CountResponse(repository.count());
    }

    public record CountResponse(long count) {
    }
}
