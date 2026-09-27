package space.grayt.teremok.content.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import space.grayt.teremok.content.domain.TextWorkContent;

@Repository
public class InMemoryTextWorkContentRepository implements TextWorkContentRepository {

    private final Map<UUID, TextWorkContent> textWorks = new ConcurrentHashMap<>();

    @Override
    public void save(TextWorkContent textWork) {
        textWorks.put(textWork.id(), textWork);
    }

    @Override
    public Optional<TextWorkContent> findById(UUID id) {
        return Optional.ofNullable(textWorks.get(id));
    }

    @Override
    public long count() {
        return textWorks.size();
    }
}
