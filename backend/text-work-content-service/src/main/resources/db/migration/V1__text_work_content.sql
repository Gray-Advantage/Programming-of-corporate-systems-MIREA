CREATE TABLE text_works (
    id UUID PRIMARY KEY
);

CREATE TABLE voice_parts (
    id                    UUID         PRIMARY KEY,
    text_work_id          UUID         NOT NULL REFERENCES text_works (id) ON DELETE CASCADE,
    sort_order            INTEGER      NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    total_fragments_count INTEGER      NOT NULL CHECK (total_fragments_count > 0)
);

CREATE TABLE segments (
    id                 UUID         PRIMARY KEY,
    text_work_id       UUID         NOT NULL REFERENCES text_works (id) ON DELETE CASCADE,
    order_in_text_work INTEGER      NOT NULL CHECK (order_in_text_work > 0),
    name               VARCHAR(300) NOT NULL,
    UNIQUE (text_work_id, order_in_text_work)
);

-- The voice part reference is checked at commit: Hibernate may insert a fragment before its voice part.
CREATE TABLE fragments (
    id               UUID          PRIMARY KEY,
    segment_id       UUID          NOT NULL REFERENCES segments (id) ON DELETE CASCADE,
    order_in_segment INTEGER       NOT NULL CHECK (order_in_segment > 0),
    content          VARCHAR(4000) NOT NULL,
    voice_part_id    UUID          NOT NULL REFERENCES voice_parts (id) DEFERRABLE INITIALLY DEFERRED,
    UNIQUE (segment_id, order_in_segment)
);

CREATE INDEX voice_parts_text_work_idx ON voice_parts (text_work_id);
CREATE INDEX fragments_voice_part_idx ON fragments (voice_part_id);
