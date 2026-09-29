package space.grayt.teremok.catalog.repository;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import space.grayt.teremok.catalog.domain.CatalogTextWork;
import space.grayt.teremok.events.TextWorkAddedEvent.OriginType;
import space.grayt.teremok.events.TextWorkAddedEvent.SegmentType;
import space.grayt.teremok.events.TextWorkAddedEvent.TextWorkOrigin;

/** Row of catalog_text_works. The domain record stays immutable; JPA needs this mutable twin. */
@Entity
@Table(name = "catalog_text_works")
public class CatalogTextWorkEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private LocalDate publicationDate;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OriginType originType;

    private String translatedFrom;

    @Column(nullable = false)
    private int segmentsCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SegmentType segmentType;

    @ElementCollection
    @CollectionTable(name = "catalog_text_work_authors", joinColumns = @JoinColumn(name = "text_work_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "author", nullable = false)
    private List<String> authors = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "catalog_text_work_translators", joinColumns = @JoinColumn(name = "text_work_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "translator", nullable = false)
    private List<String> translators = new ArrayList<>();

    protected CatalogTextWorkEntity() {
    }

    static CatalogTextWorkEntity from(CatalogTextWork textWork) {
        CatalogTextWorkEntity entity = new CatalogTextWorkEntity();
        entity.id = textWork.id();
        entity.name = textWork.name();
        entity.publicationDate = textWork.publicationDate();
        entity.language = textWork.language();
        entity.originType = textWork.origin().type();
        entity.translatedFrom = textWork.origin().translatedFrom();
        entity.segmentsCount = textWork.segmentsCount();
        entity.segmentType = textWork.segmentType();
        entity.authors = new ArrayList<>(textWork.authors());
        entity.translators = new ArrayList<>(textWork.origin().translators());
        return entity;
    }

    CatalogTextWork toDomain() {
        return new CatalogTextWork(id, List.copyOf(authors), name, publicationDate, language,
                new TextWorkOrigin(originType, translatedFrom, List.copyOf(translators)), segmentsCount, segmentType);
    }
}
