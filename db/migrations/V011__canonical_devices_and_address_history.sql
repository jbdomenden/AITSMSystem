CREATE TABLE IF NOT EXISTS monitoring_device_addresses (
  id BIGSERIAL PRIMARY KEY,
  device_id INTEGER NOT NULL REFERENCES monitoring_devices(id) ON DELETE CASCADE,
  collector_id UUID REFERENCES monitoring_collectors(id) ON DELETE SET NULL,
  ip_address VARCHAR(45) NOT NULL,
  mac_address VARCHAR(32),
  first_seen_at TIMESTAMP NOT NULL,
  last_seen_at TIMESTAMP NOT NULL,
  observation_count BIGINT NOT NULL DEFAULT 1,
  UNIQUE(device_id, ip_address)
);

CREATE INDEX IF NOT EXISTS monitoring_device_addresses_device_idx ON monitoring_device_addresses(device_id);
CREATE INDEX IF NOT EXISTS monitoring_device_addresses_collector_idx ON monitoring_device_addresses(collector_id);
CREATE INDEX IF NOT EXISTS monitoring_device_addresses_last_seen_idx ON monitoring_device_addresses(last_seen_at);

INSERT INTO monitoring_device_addresses (
  device_id, collector_id, ip_address, mac_address, first_seen_at, last_seen_at, observation_count
)
SELECT id, collector_id, ip_address, mac_address, received_at, last_seen_at, 1
FROM monitoring_devices
ON CONFLICT (device_id, ip_address) DO NOTHING;

-- Legacy collector versions did not submit a hardware UUID. Consolidate only
-- stale agent rows that share an exact normalized hostname and have no stable
-- identity. Active devices and LAN-observed peers are deliberately excluded.
WITH ranked AS (
  SELECT id,
         FIRST_VALUE(id) OVER (
           PARTITION BY lower(trim(hostname))
           ORDER BY received_at DESC, id DESC
         ) AS canonical_id,
         ROW_NUMBER() OVER (
           PARTITION BY lower(trim(hostname))
           ORDER BY received_at DESC, id DESC
         ) AS duplicate_rank
  FROM monitoring_devices
  WHERE telemetry_available = TRUE
    AND device_uuid IS NULL
    AND identity_key LIKE 'legacy:%'
    AND received_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
), duplicate_addresses AS (
  SELECT ranked.canonical_id,
         address.collector_id,
         address.ip_address,
         address.mac_address,
         address.first_seen_at,
         address.last_seen_at,
         address.observation_count
  FROM ranked
  JOIN monitoring_device_addresses address ON address.device_id = ranked.id
  WHERE ranked.duplicate_rank > 1
)
INSERT INTO monitoring_device_addresses (
  device_id, collector_id, ip_address, mac_address, first_seen_at, last_seen_at, observation_count
)
SELECT canonical_id, collector_id, ip_address, mac_address, first_seen_at, last_seen_at, observation_count
FROM duplicate_addresses
ON CONFLICT (device_id, ip_address) DO UPDATE
SET first_seen_at = LEAST(monitoring_device_addresses.first_seen_at, EXCLUDED.first_seen_at),
    last_seen_at = GREATEST(monitoring_device_addresses.last_seen_at, EXCLUDED.last_seen_at),
    observation_count = monitoring_device_addresses.observation_count + EXCLUDED.observation_count,
    collector_id = EXCLUDED.collector_id,
    mac_address = COALESCE(EXCLUDED.mac_address, monitoring_device_addresses.mac_address);

WITH ranked AS (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY lower(trim(hostname))
           ORDER BY received_at DESC, id DESC
         ) AS duplicate_rank
  FROM monitoring_devices
  WHERE telemetry_available = TRUE
    AND device_uuid IS NULL
    AND identity_key LIKE 'legacy:%'
    AND received_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
)
DELETE FROM monitoring_devices device
USING ranked
WHERE device.id = ranked.id AND ranked.duplicate_rank > 1;
