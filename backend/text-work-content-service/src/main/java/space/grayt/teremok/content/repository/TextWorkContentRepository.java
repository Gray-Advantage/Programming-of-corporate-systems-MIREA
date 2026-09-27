package space.grayt.teremok.content.repository;

import space.grayt.teremok.content.domain.TextWorkContent;

public interface TextWorkContentRepository {

    void save(TextWorkContent textWork);

    long count();
}
