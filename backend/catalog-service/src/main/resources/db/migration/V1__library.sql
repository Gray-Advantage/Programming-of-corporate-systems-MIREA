CREATE TABLE IF NOT EXISTS catalog_text_work (id UUID PRIMARY KEY,name TEXT NOT NULL,publication_date DATE,language VARCHAR(32) NOT NULL,origin_type VARCHAR(32) NOT NULL,translated_from VARCHAR(32),segments_count INTEGER NOT NULL CHECK(segments_count>=0),segment_type VARCHAR(32) NOT NULL);
CREATE TABLE IF NOT EXISTS catalog_text_work_author(text_work_id UUID NOT NULL REFERENCES catalog_text_work(id) ON DELETE CASCADE,author_order INTEGER NOT NULL,author TEXT NOT NULL,PRIMARY KEY(text_work_id,author_order));
CREATE TABLE IF NOT EXISTS catalog_text_work_translator(text_work_id UUID NOT NULL REFERENCES catalog_text_work(id) ON DELETE CASCADE,translator_order INTEGER NOT NULL,translator TEXT NOT NULL,PRIMARY KEY(text_work_id,translator_order));
CREATE TABLE library_collection(id UUID PRIMARY KEY,name VARCHAR(120) NOT NULL UNIQUE,active BOOLEAN NOT NULL DEFAULT TRUE);
INSERT INTO library_collection VALUES ('10000000-0000-0000-0000-000000000099','Художественная литература',true);
CREATE TABLE library_work (
 id UUID PRIMARY KEY,code VARCHAR(64) NOT NULL UNIQUE,title VARCHAR(240) NOT NULL,author VARCHAR(300) NOT NULL,
 description VARCHAR(3000) NOT NULL DEFAULT '',language VARCHAR(32) NOT NULL DEFAULT 'rus',
 collection_id UUID NOT NULL REFERENCES library_collection(id),status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 target_minutes INTEGER NOT NULL CHECK(target_minutes BETWEEN 1 AND 1440),featured BOOLEAN NOT NULL DEFAULT FALSE,
 roles_count INTEGER NOT NULL DEFAULT 1,complexity VARCHAR(16) NOT NULL,archived BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,version BIGINT NOT NULL DEFAULT 0,
 script TEXT NOT NULL DEFAULT '',source_payload TEXT
);
CREATE TABLE library_tag(work_id UUID NOT NULL REFERENCES library_work(id) ON DELETE CASCADE,tag VARCHAR(60) NOT NULL,PRIMARY KEY(work_id,tag));
CREATE TABLE library_note(id UUID PRIMARY KEY,work_id UUID NOT NULL REFERENCES library_work(id) ON DELETE CASCADE,author_id UUID NOT NULL,author_name VARCHAR(160) NOT NULL,content VARCHAR(3000) NOT NULL,pinned BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL);
CREATE TABLE library_audit(id UUID PRIMARY KEY,user_id UUID NOT NULL,actor VARCHAR(160) NOT NULL,event_type VARCHAR(60) NOT NULL,entity_type VARCHAR(60) NOT NULL,entity_id UUID NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,description VARCHAR(500) NOT NULL,correlation_id VARCHAR(64) NOT NULL);
CREATE TABLE library_import(id UUID PRIMARY KEY,user_id UUID NOT NULL,mode VARCHAR(20) NOT NULL,payload TEXT NOT NULL,expires_at TIMESTAMPTZ NOT NULL,applied BOOLEAN NOT NULL DEFAULT FALSE);
CREATE TABLE library_outbox(id UUID PRIMARY KEY,payload TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX library_search ON library_work(lower(title));
CREATE INDEX library_code_search ON library_work(lower(code));
CREATE INDEX library_collection_status ON library_work(collection_id,status,archived);
CREATE INDEX library_tag_search ON library_tag(tag);
CREATE INDEX library_note_work ON library_note(work_id,created_at);
CREATE INDEX library_audit_time ON library_audit(occurred_at DESC);
