ALTER TABLE media_asset
    MODIFY COLUMN provider_file_id VARCHAR(1024) NULL,
    ADD COLUMN external_url VARCHAR(2048) NULL,
    ADD CONSTRAINT chk_media_asset_content CHECK (
        (media_type = 'VIDEO' AND provider = 'TELEGRAM'
            AND provider_file_id IS NOT NULL AND CHAR_LENGTH(TRIM(provider_file_id)) > 0
            AND external_url IS NULL)
        OR
        (media_type = 'LINK' AND provider = 'EXTERNAL'
            AND external_url IS NOT NULL AND CHAR_LENGTH(TRIM(external_url)) > 0
            AND provider_file_id IS NULL)
    );
