ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS reviewed_resolution TEXT,
    ADD COLUMN IF NOT EXISTS resolution_updated_by VARCHAR(150),
    ADD COLUMN IF NOT EXISTS resolution_updated_at TIMESTAMP;
