package space.grayt.teremok.textwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import space.grayt.teremok.backend.BackendClient;
import space.grayt.teremok.client.contract.CatalogTextWorkResponse;
import space.grayt.teremok.client.contract.TextWorkContentResponse;
import space.grayt.teremok.client.contract.TextWorkContentResponse.Segment;
import space.grayt.teremok.client.contract.TextWorkContentResponse.VoicePartFragment;
import space.grayt.teremok.domain.TextWork;
import space.grayt.teremok.domain.TextWorkFragment;
import space.grayt.teremok.domain.VoicePart;

/** A snapshot of text works assembled from the two backend services. */
public final class BackendTextWorkCatalog implements TextWorkCatalog {

    private final Map<String, TextWork> textWorks = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public BackendTextWorkCatalog(BackendClient backend) {
        try {
            for (CatalogTextWorkResponse textWork : backend.catalogTextWorks()) {
                load(backend, textWork);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            warnings.add("Загрузка произведений из backend прервана.");
        } catch (IOException | RuntimeException exception) {
            warnings.add("Не удалось загрузить каталог из backend: " + exception.getMessage());
        }
    }

    @Override
    public List<TextWork> all() {
        return List.copyOf(textWorks.values());
    }

    @Override
    public Optional<TextWork> find(String textWorkId) {
        return Optional.ofNullable(textWorks.get(textWorkId));
    }

    @Override
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private void load(BackendClient backend, CatalogTextWorkResponse textWork) throws InterruptedException {
        try {
            TextWorkContentResponse content = backend.textWorkContent(textWork.id());
            textWorks.put(textWork.id().toString(), toTextWork(textWork, content));
        } catch (IOException | RuntimeException exception) {
            warnings.add("Произведение «" + textWork.name() + "» пропущено: " + exception.getMessage());
        }
    }

    private static TextWork toTextWork(
            CatalogTextWorkResponse textWork,
            TextWorkContentResponse content) {
        if (!textWork.id().equals(content.id())) {
            throw new IllegalArgumentException("CatalogService и TextWorkContentService вернули разные id");
        }

        Map<String, VoicePart> voiceParts = new LinkedHashMap<>();
        content.voiceParts().forEach(voicePart -> voiceParts.put(
                voicePart.id().toString(),
                new VoicePart(voicePart.id().toString(), voicePart.name())));

        List<TextWorkFragment> fragments = new ArrayList<>();
        List<Segment> segments = content.segments().stream()
                .sorted(Comparator.comparingInt(Segment::orderInTextWork))
                .toList();
        for (Segment segment : segments) {
            List<VoicePartFragment> segmentFragments = segment.fragments().stream()
                    .sorted(Comparator.comparingInt(VoicePartFragment::orderInSegment))
                    .toList();
            for (VoicePartFragment fragment : segmentFragments) {
                requireKnownVoicePart(voiceParts, fragment.voicePartId());
                fragments.add(new TextWorkFragment(
                        fragments.size() + 1,
                        fragment.voicePartId().toString(),
                        fragment.content()));
            }
        }
        return new TextWork(textWork.id().toString(), textWork.name(), fragments, voiceParts);
    }

    private static void requireKnownVoicePart(Map<String, VoicePart> voiceParts, UUID voicePartId) {
        if (voicePartId == null || !voiceParts.containsKey(voicePartId.toString())) {
            throw new IllegalArgumentException("фрагмент ссылается на неизвестный voicePart " + voicePartId);
        }
    }
}
