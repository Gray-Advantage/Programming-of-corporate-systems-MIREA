package space.grayt.teremok.content.repository;

import java.util.Optional;
import java.util.UUID;
import space.grayt.teremok.content.domain.TextWorkContent;

public interface TextWorkContentRepository {

    void save(TextWorkContent textWork);

    Optional<TextWorkContent> findById(UUID id);

    long count();
}
