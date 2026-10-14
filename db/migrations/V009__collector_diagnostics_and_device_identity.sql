ALTER TABLE monitoring_collectors
  ADD COLUMN IF NOT EXISTS agent_version VARCHAR(40),
  ADD COLUMN IF NOT EXISTS service_started_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS last_error VARCHAR(500),
  ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0;

ALTER TABLE monitoring_devices
  ADD COLUMN IF NOT EXISTS identity_key VARCHAR(220),
  ADD COLUMN IF NOT EXISTS device_uuid VARCHAR(160);

UPDATE monitoring_devices
SET identity_key = CASE
  WHEN mac_address IS NOT NULL AND length(regexp_replace(mac_address, '[^0-9A-Fa-f]', '', 'g')) = 12
    THEN 'mac:' || upper(regexp_replace(mac_address, '[^0-9A-Fa-f]', '', 'g'))
  ELSE 'legacy:' || id::text
END
WHERE identity_key IS NULL;

DELETE FROM monitoring_devices older
USING monitoring_devices newer
WHERE older.id <> newer.id
  AND older.identity_key = newer.identity_key
  AND (older.received_at < newer.received_at OR (older.received_at = newer.received_at AND older.id < newer.id));

CREATE UNIQUE INDEX IF NOT EXISTS monitoring_devices_identity_unique_idx
  ON monitoring_devices(identity_key) WHERE identity_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS monitoring_devices_uuid_idx ON monitoring_devices(device_uuid);
