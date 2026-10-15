-- A physical Windows device can change network adapters and IP addresses. When
-- exactly one UUID-backed device exists for a hostname, preserve the address
-- history of older UUID-less agent rows and consolidate them into that device.
WITH uuid_hosts AS (
  SELECT lower(trim(hostname)) AS normalized_hostname
  FROM monitoring_devices
  WHERE telemetry_available = TRUE
    AND trim(hostname) <> ''
  GROUP BY lower(trim(hostname))
  HAVING COUNT(DISTINCT lower(device_uuid)) FILTER (WHERE device_uuid IS NOT NULL) = 1
), canonical AS (
  SELECT host.normalized_hostname,
         (
           SELECT device.id
           FROM monitoring_devices device
           WHERE lower(trim(device.hostname)) = host.normalized_hostname
             AND device.device_uuid IS NOT NULL
           ORDER BY device.received_at DESC, device.id DESC
           LIMIT 1
         ) AS canonical_id
  FROM uuid_hosts host
), duplicate_addresses AS (
  SELECT canonical.canonical_id,
         address.collector_id,
         address.ip_address,
         address.mac_address,
         address.first_seen_at,
         address.last_seen_at,
         address.observation_count
  FROM canonical
  JOIN monitoring_devices duplicate
    ON lower(trim(duplicate.hostname)) = canonical.normalized_hostname
   AND duplicate.id <> canonical.canonical_id
   AND duplicate.telemetry_available = TRUE
   AND duplicate.device_uuid IS NULL
   AND duplicate.last_seen_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'
  JOIN monitoring_device_addresses address ON address.device_id = duplicate.id
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

WITH uuid_hosts AS (
  SELECT lower(trim(hostname)) AS normalized_hostname
  FROM monitoring_devices
  WHERE telemetry_available = TRUE
    AND trim(hostname) <> ''
  GROUP BY lower(trim(hostname))
  HAVING COUNT(DISTINCT lower(device_uuid)) FILTER (WHERE device_uuid IS NOT NULL) = 1
), canonical AS (
  SELECT host.normalized_hostname,
         (
           SELECT device.id
           FROM monitoring_devices device
           WHERE lower(trim(device.hostname)) = host.normalized_hostname
             AND device.device_uuid IS NOT NULL
           ORDER BY device.received_at DESC, device.id DESC
           LIMIT 1
         ) AS canonical_id
  FROM uuid_hosts host
)
DELETE FROM monitoring_devices duplicate
USING canonical
WHERE lower(trim(duplicate.hostname)) = canonical.normalized_hostname
  AND duplicate.id <> canonical.canonical_id
  AND duplicate.telemetry_available = TRUE
  AND duplicate.device_uuid IS NULL
  AND duplicate.last_seen_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes';
