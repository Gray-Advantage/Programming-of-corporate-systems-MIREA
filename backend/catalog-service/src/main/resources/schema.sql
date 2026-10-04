CREATE TABLE IF NOT EXISTS catalog_text_work (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    publication_date DATE,
    language VARCHAR(32) NOT NULL,
    origin_type VARCHAR(32) NOT NULL,
    translated_from VARCHAR(32),
    segments_count INTEGER NOT NULL CHECK (segments_count >= 0),
    segment_type VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS catalog_text_work_author (
    text_work_id UUID NOT NULL REFERENCES catalog_text_work(id) ON DELETE CASCADE,
    author_order INTEGER NOT NULL CHECK (author_order >= 0),
    author TEXT NOT NULL,
    PRIMARY KEY (text_work_id, author_order)
);

CREATE TABLE IF NOT EXISTS catalog_text_work_translator (
    text_work_id UUID NOT NULL REFERENCES catalog_text_work(id) ON DELETE CASCADE,
    translator_order INTEGER NOT NULL CHECK (translator_order >= 0),
    translator TEXT NOT NULL,
    PRIMARY KEY (text_work_id, translator_order)
);
