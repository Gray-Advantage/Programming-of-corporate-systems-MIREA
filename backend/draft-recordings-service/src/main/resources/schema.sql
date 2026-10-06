CREATE TABLE IF NOT EXISTS draft_user (
    id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS draft_text_work (
    id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS draft_segment (
    id UUID PRIMARY KEY,
    text_work_id UUID NOT NULL REFERENCES draft_text_work(id) ON DELETE CASCADE,
    order_in_text_work INTEGER NOT NULL CHECK (order_in_text_work > 0),
    UNIQUE (text_work_id, order_in_text_work)
);

CREATE TABLE IF NOT EXISTS draft_role (
    id UUID PRIMARY KEY,
    text_work_id UUID NOT NULL REFERENCES draft_text_work(id) ON DELETE CASCADE,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS draft_fragment (
    id UUID PRIMARY KEY,
    segment_id UUID NOT NULL REFERENCES draft_segment(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES draft_role(id) ON DELETE CASCADE,
    order_in_segment INTEGER NOT NULL CHECK (order_in_segment > 0),
    UNIQUE (segment_id, order_in_segment)
);

CREATE TABLE IF NOT EXISTS draft_fragment_recording (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES draft_user(id),
    fragment_id UUID NOT NULL REFERENCES draft_fragment(id),
    object_key VARCHAR(500) NOT NULL UNIQUE,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS draft_fragment_recording_lookup
    ON draft_fragment_recording (user_id, fragment_id, created_at);

CREATE TABLE IF NOT EXISTS draft_role_publication (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES draft_user(id),
    text_work_id UUID NOT NULL REFERENCES draft_text_work(id),
    role_id UUID NOT NULL REFERENCES draft_role(id),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL
);
