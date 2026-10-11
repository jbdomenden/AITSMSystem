CREATE TABLE IF NOT EXISTS monitoring_collectors (
  id UUID PRIMARY KEY, site_id VARCHAR(100) NOT NULL, name VARCHAR(150) NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE, enrolled_at TIMESTAMP NOT NULL,
  last_seen_at TIMESTAMP, revoked_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS monitoring_collectors_site_idx ON monitoring_collectors(site_id);
CREATE INDEX IF NOT EXISTS monitoring_collectors_last_seen_idx ON monitoring_collectors(last_seen_at);

CREATE TABLE IF NOT EXISTS monitoring_devices (
  id SERIAL PRIMARY KEY, collector_id UUID NOT NULL REFERENCES monitoring_collectors(id) ON DELETE CASCADE,
  device_key VARCHAR(200) NOT NULL, hostname VARCHAR(200) NOT NULL, ip_address VARCHAR(45) NOT NULL,
  mac_address VARCHAR(32), os_details VARCHAR(300), reachable BOOLEAN NOT NULL,
  telemetry_available BOOLEAN NOT NULL, cpu_usage_percent NUMERIC(5,2), memory_usage_percent NUMERIC(5,2),
  storage_total_bytes BIGINT, storage_used_bytes BIGINT, measurement_at TIMESTAMP,
  received_at TIMESTAMP NOT NULL, last_seen_at TIMESTAMP NOT NULL,
  UNIQUE(collector_id, device_key)
);
CREATE INDEX IF NOT EXISTS monitoring_devices_ip_idx ON monitoring_devices(ip_address);
CREATE INDEX IF NOT EXISTS monitoring_devices_received_idx ON monitoring_devices(received_at);

CREATE TABLE IF NOT EXISTS monitoring_discovery_requests (
  id UUID PRIMARY KEY, collector_id UUID NOT NULL REFERENCES monitoring_collectors(id) ON DELETE CASCADE,
  status VARCHAR(20) NOT NULL, requested_at TIMESTAMP NOT NULL, completed_at TIMESTAMP,
  error_message VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS monitoring_discovery_status_idx ON monitoring_discovery_requests(status);
