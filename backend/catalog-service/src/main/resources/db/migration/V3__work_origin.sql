ALTER TABLE library_work ADD COLUMN origin_type VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL';
UPDATE library_work SET origin_type=coalesce(source_payload::jsonb->'origin'->>'type','ORIGINAL') WHERE source_payload IS NOT NULL;
