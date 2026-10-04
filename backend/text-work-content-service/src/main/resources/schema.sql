CREATE TABLE IF NOT EXISTS text_work_content (
    id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS voice_part (
    id UUID PRIMARY KEY,
    text_work_id UUID NOT NULL REFERENCES text_work_content(id) ON DELETE CASCADE,
    voice_part_order INTEGER NOT NULL CHECK (voice_part_order >= 0),
    name TEXT NOT NULL,
    total_fragments_count INTEGER NOT NULL CHECK (total_fragments_count >= 0),
    UNIQUE (text_work_id, voice_part_order)
);

CREATE TABLE IF NOT EXISTS segment (
    id UUID PRIMARY KEY,
    text_work_id UUID NOT NULL REFERENCES text_work_content(id) ON DELETE CASCADE,
    segment_order INTEGER NOT NULL CHECK (segment_order >= 0),
    name TEXT NOT NULL,
    UNIQUE (text_work_id, segment_order)
);

CREATE TABLE IF NOT EXISTS voice_part_fragment (
    id UUID PRIMARY KEY,
    text_work_id UUID NOT NULL REFERENCES text_work_content(id) ON DELETE CASCADE,
    segment_id UUID NOT NULL REFERENCES segment(id) ON DELETE CASCADE,
    fragment_order INTEGER NOT NULL CHECK (fragment_order >= 0),
    content TEXT NOT NULL,
    voice_part_id UUID NOT NULL REFERENCES voice_part(id),
    UNIQUE (segment_id, fragment_order)
);

CREATE INDEX IF NOT EXISTS voice_part_text_work_idx ON voice_part(text_work_id);
CREATE INDEX IF NOT EXISTS segment_text_work_idx ON segment(text_work_id);
CREATE INDEX IF NOT EXISTS voice_part_fragment_text_work_idx ON voice_part_fragment(text_work_id);
CREATE INDEX IF NOT EXISTS voice_part_fragment_segment_idx ON voice_part_fragment(segment_id);
