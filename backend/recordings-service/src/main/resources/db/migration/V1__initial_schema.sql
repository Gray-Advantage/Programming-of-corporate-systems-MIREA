CREATE TABLE IF NOT EXISTS recording_user (
    id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS recording_text_work (
    id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS recording_segment (
    id UUID PRIMARY KEY,
    text_work_id UUID NOT NULL REFERENCES recording_text_work(id) ON DELETE CASCADE,
    order_in_text_work INTEGER NOT NULL CHECK (order_in_text_work > 0),
    UNIQUE (text_work_id, order_in_text_work)
);

CREATE TABLE IF NOT EXISTS recording_role (
    id UUID PRIMARY KEY,
    text_work_id UUID NOT NULL REFERENCES recording_text_work(id) ON DELETE CASCADE,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS recording_fragment (
    id UUID PRIMARY KEY,
    segment_id UUID NOT NULL REFERENCES recording_segment(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES recording_role(id) ON DELETE CASCADE,
    order_in_segment INTEGER NOT NULL CHECK (order_in_segment > 0),
    UNIQUE (segment_id, order_in_segment)
);

CREATE TABLE IF NOT EXISTS role_recording (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES recording_user(id),
    text_work_id UUID NOT NULL REFERENCES recording_text_work(id),
    role_id UUID NOT NULL REFERENCES recording_role(id),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS fragment_recording (
    id UUID PRIMARY KEY,
    role_recording_id UUID NOT NULL REFERENCES role_recording(id) ON DELETE CASCADE,
    fragment_id UUID NOT NULL REFERENCES recording_fragment(id),
    object_key VARCHAR(500) NOT NULL UNIQUE,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    UNIQUE (role_recording_id, fragment_id)
);

CREATE TABLE IF NOT EXISTS render_job (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES recording_user(id),
    text_work_id UUID NOT NULL REFERENCES recording_text_work(id),
    output_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    error TEXT
);

CREATE TABLE IF NOT EXISTS render_job_role (
    render_job_id UUID NOT NULL REFERENCES render_job(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES recording_role(id),
    role_recording_id UUID NOT NULL REFERENCES role_recording(id),
    PRIMARY KEY (render_job_id, role_id)
);

CREATE TABLE IF NOT EXISTS render_output (
    id UUID PRIMARY KEY,
    render_job_id UUID NOT NULL REFERENCES render_job(id) ON DELETE CASCADE,
    segment_id UUID REFERENCES recording_segment(id),
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(120) NOT NULL
);
