CREATE DATABASE aitsm_db;

-- Run below in aitsm_db
CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(200) UNIQUE NOT NULL,
  company VARCHAR(150) NOT NULL,
  department VARCHAR(120) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'end-user',
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  verification_code VARCHAR(12),
  verification_expires_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS eula_acceptance (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id),
  eula_version VARCHAR(20) NOT NULL,
  accepted_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
  id SERIAL PRIMARY KEY,
  device_name VARCHAR(150) NOT NULL,
  ip_address VARCHAR(45) NOT NULL,
  department VARCHAR(120) NOT NULL,
  assigned_user VARCHAR(150) NOT NULL,
  cpu_usage INT NOT NULL DEFAULT 0,
  memory_usage INT NOT NULL DEFAULT 0,
  status VARCHAR(50) NOT NULL,
  last_seen TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS tickets (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id),
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  priority VARCHAR(20) NOT NULL,
  category VARCHAR(80) NOT NULL,
  status VARCHAR(30) NOT NULL,
  assigned_to VARCHAR(150),
  device_id INT REFERENCES devices(id),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_history (
  id SERIAL PRIMARY KEY,
  ticket_id INT REFERENCES tickets(id),
  status VARCHAR(30) NOT NULL,
  updated_by VARCHAR(150) NOT NULL,
  timestamp TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS notifications (
  id SERIAL PRIMARY KEY,
  user_id INT REFERENCES users(id),
  message TEXT NOT NULL,
  type VARCHAR(40) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS sla_policies (
  id SERIAL PRIMARY KEY,
  priority VARCHAR(20) NOT NULL,
  response_time INT NOT NULL,
  resolution_time INT NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_articles (
  id SERIAL PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  category VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id SERIAL PRIMARY KEY,
  user_id INT,
  action VARCHAR(160) NOT NULL,
  entity VARCHAR(80) NOT NULL,
  timestamp TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_assets (
  id SERIAL PRIMARY KEY,
  asset_tag VARCHAR(120) UNIQUE,
  hostname VARCHAR(200),
  full_device_name VARCHAR(255),
  manufacturer VARCHAR(150),
  model VARCHAR(180),
  processor_name VARCHAR(255),
  processor_speed VARCHAR(100),
  installed_ram VARCHAR(100),
  usable_ram VARCHAR(100),
  ram_speed VARCHAR(100),
  gpu_name VARCHAR(255),
  gpu_memory VARCHAR(100),
  storage_total VARCHAR(100),
  storage_used VARCHAR(100),
  storage_free VARCHAR(100),
  storage_breakdown_json TEXT,
  system_type VARCHAR(180),
  os_name VARCHAR(180),
  os_edition VARCHAR(180),
  os_version VARCHAR(120),
  os_build VARCHAR(120),
  installed_on VARCHAR(120),
  domain_or_workgroup VARCHAR(160),
  device_uuid VARCHAR(160),
  product_id VARCHAR(160),
  pen_touch_support VARCHAR(120),
  ip_address VARCHAR(45),
  mac_address VARCHAR(80),
  connection_source VARCHAR(80),
  status VARCHAR(50) NOT NULL DEFAULT 'offline',
  assigned_department VARCHAR(150),
  assigned_user VARCHAR(180),
  notes TEXT,
  last_seen_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS inventory_asset_snapshots (
  id SERIAL PRIMARY KEY,
  asset_id INT NOT NULL REFERENCES inventory_assets(id) ON DELETE CASCADE,
  snapshot_source VARCHAR(80) NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
