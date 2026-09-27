package space.grayt.teremok.client.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CatalogTextWorkResponse(
        UUID id,
        List<String> authors,
        String name,
        LocalDate publicationDate,
        String language,
        TextWorkOrigin origin,
        int segmentsCount,
        SegmentType segmentType) {

    public CatalogTextWorkResponse {
        authors = List.copyOf(authors);
    }

    public record TextWorkOrigin(
            OriginType type,
            String translatedFrom,
            List<String> translators) {

        public TextWorkOrigin {
            translators = List.copyOf(translators);
        }
    }

    public enum OriginType {
        ORIGINAL,
        TRANSLATION
    }

    public enum SegmentType {
        CHAPTER,
        ACT,
        SINGLE_SEGMENT
    }
}
