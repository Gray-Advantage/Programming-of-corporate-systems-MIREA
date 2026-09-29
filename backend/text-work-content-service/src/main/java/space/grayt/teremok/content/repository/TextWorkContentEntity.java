package space.grayt.teremok.content.repository;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import space.grayt.teremok.content.domain.TextWorkContent;
import space.grayt.teremok.events.TextWorkAddedEvent.SegmentPayload;
import space.grayt.teremok.events.TextWorkAddedEvent.VoicePartFragmentPayload;
import space.grayt.teremok.events.TextWorkAddedEvent.VoicePartPayload;

/** A text work with its segments, fragments and voice parts; children are saved and deleted with it. */
@Entity
@Table(name = "text_works")
public class TextWorkContentEntity {

    @Id
    private UUID id;

    @OneToMany(mappedBy = "textWork", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<VoicePartEntity> voiceParts = new ArrayList<>();

    @OneToMany(mappedBy = "textWork", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderInTextWork")
    private List<SegmentEntity> segments = new ArrayList<>();

    protected TextWorkContentEntity() {
    }

    static TextWorkContentEntity from(TextWorkContent content) {
        TextWorkContentEntity entity = new TextWorkContentEntity();
        entity.id = content.id();
        for (int i = 0; i < content.voiceParts().size(); i++) {
            entity.voiceParts.add(VoicePartEntity.from(entity, i, content.voiceParts().get(i)));
        }
        for (SegmentPayload segment : content.segments()) {
            entity.segments.add(SegmentEntity.from(entity, segment));
        }
        return entity;
    }

    TextWorkContent toDomain() {
        return new TextWorkContent(id,
                segments.stream().map(SegmentEntity::toPayload).toList(),
                voiceParts.stream().map(VoicePartEntity::toPayload).toList());
    }

    @Entity
    @Table(name = "voice_parts")
    public static class VoicePartEntity {

        @Id
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "text_work_id")
        private TextWorkContentEntity textWork;

        @Column(nullable = false)
        private int sortOrder;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false)
        private int totalFragmentsCount;

        protected VoicePartEntity() {
        }

        static VoicePartEntity from(TextWorkContentEntity textWork, int sortOrder, VoicePartPayload payload) {
            VoicePartEntity entity = new VoicePartEntity();
            entity.id = payload.id();
            entity.textWork = textWork;
            entity.sortOrder = sortOrder;
            entity.name = payload.name();
            entity.totalFragmentsCount = payload.totalFragmentsCount();
            return entity;
        }

        VoicePartPayload toPayload() {
            return new VoicePartPayload(id, name, totalFragmentsCount);
        }
    }

    @Entity
    @Table(name = "segments")
    public static class SegmentEntity {

        @Id
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "text_work_id")
        private TextWorkContentEntity textWork;

        @Column(nullable = false)
        private int orderInTextWork;

        @Column(nullable = false)
        private String name;

        @OneToMany(mappedBy = "segment", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("orderInSegment")
        private List<FragmentEntity> fragments = new ArrayList<>();

        protected SegmentEntity() {
        }

        static SegmentEntity from(TextWorkContentEntity textWork, SegmentPayload payload) {
            SegmentEntity entity = new SegmentEntity();
            entity.id = payload.id();
            entity.textWork = textWork;
            entity.orderInTextWork = payload.orderInTextWork();
            entity.name = payload.name();
            for (VoicePartFragmentPayload fragment : payload.fragments()) {
                entity.fragments.add(FragmentEntity.from(entity, fragment));
            }
            return entity;
        }

        SegmentPayload toPayload() {
            return new SegmentPayload(id, orderInTextWork, name,
                    fragments.stream().map(FragmentEntity::toPayload).toList());
        }
    }

    @Entity
    @Table(name = "fragments")
    public static class FragmentEntity {

        @Id
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "segment_id")
        private SegmentEntity segment;

        @Column(nullable = false)
        private int orderInSegment;

        @Column(nullable = false)
        private String content;

        @Column(nullable = false)
        private UUID voicePartId;

        protected FragmentEntity() {
        }

        static FragmentEntity from(SegmentEntity segment, VoicePartFragmentPayload payload) {
            FragmentEntity entity = new FragmentEntity();
            entity.id = payload.id();
            entity.segment = segment;
            entity.orderInSegment = payload.orderInSegment();
            entity.content = payload.content();
            entity.voicePartId = payload.voicePartId();
            return entity;
        }

        VoicePartFragmentPayload toPayload() {
            return new VoicePartFragmentPayload(id, orderInSegment, content, voicePartId);
        }
    }
}
