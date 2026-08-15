ALTER TABLE media
    ADD COLUMN storage_provider VARCHAR(20) NULL AFTER size_bytes,
    ADD COLUMN storage_bucket VARCHAR(100) NULL AFTER storage_provider,
    ADD COLUMN object_key VARCHAR(512) NULL AFTER storage_bucket,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER object_key,
    ADD COLUMN category VARCHAR(20) NULL AFTER content_type;

CREATE UNIQUE INDEX uk_media_storage_object
    ON media (storage_provider, storage_bucket, object_key);

CREATE INDEX idx_media_uploader_url
    ON media (uploader_id, url);
