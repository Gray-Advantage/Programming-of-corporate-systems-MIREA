CREATE TABLE catalog_text_works (
    id               UUID         PRIMARY KEY,
    name             VARCHAR(300) NOT NULL,
    publication_date DATE,
    language         VARCHAR(16)  NOT NULL,
    origin_type      VARCHAR(16)  NOT NULL CHECK (origin_type IN ('ORIGINAL', 'TRANSLATION')),
    translated_from  VARCHAR(16),
    segments_count   INTEGER      NOT NULL CHECK (segments_count >= 0),
    segment_type     VARCHAR(16)  NOT NULL CHECK (segment_type IN ('CHAPTER', 'ACT', 'SINGLE_SEGMENT'))
);

CREATE TABLE catalog_text_work_authors (
    text_work_id UUID         NOT NULL REFERENCES catalog_text_works (id) ON DELETE CASCADE,
    sort_order   INTEGER      NOT NULL,
    author       VARCHAR(300) NOT NULL,
    PRIMARY KEY (text_work_id, sort_order)
);

CREATE TABLE catalog_text_work_translators (
    text_work_id UUID         NOT NULL REFERENCES catalog_text_works (id) ON DELETE CASCADE,
    sort_order   INTEGER      NOT NULL,
    translator   VARCHAR(300) NOT NULL,
    PRIMARY KEY (text_work_id, sort_order)
);
