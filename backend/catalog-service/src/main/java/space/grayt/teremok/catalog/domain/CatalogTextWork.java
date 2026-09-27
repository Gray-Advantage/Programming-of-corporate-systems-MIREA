package space.grayt.teremok.catalog.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import space.grayt.teremok.events.TextWorkAddedEvent.SegmentType;
import space.grayt.teremok.events.TextWorkAddedEvent.TextWorkOrigin;
import space.grayt.teremok.events.TextWorkAddedEvent.TextWorkPayload;

public record CatalogTextWork(
        UUID id,
        List<String> authors,
        String name,
        LocalDate publicationDate,
        String language,
        TextWorkOrigin origin,
        int segmentsCount,
        SegmentType segmentType) {

    public CatalogTextWork {
        authors = List.copyOf(authors);
    }

    public static CatalogTextWork from(TextWorkPayload source) {
        return new CatalogTextWork(
                source.id(),
                source.authors(),
                source.name(),
                source.publicationDate(),
                source.language(),
                source.origin(),
                source.segments().size(),
                source.segmentType());
    }
}
