ALTER TABLE tickets
  ADD COLUMN IF NOT EXISTS sla_response_target_minutes INTEGER,
  ADD COLUMN IF NOT EXISTS sla_resolution_target_minutes INTEGER,
  ADD COLUMN IF NOT EXISTS sla_response_due_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS sla_resolution_due_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS sla_first_response_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS sla_resolved_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS sla_response_warning_level INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS sla_resolution_warning_level INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS sla_response_breached_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS sla_resolution_breached_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS sla_reopened_count INTEGER NOT NULL DEFAULT 0;

UPDATE tickets t
SET sla_response_target_minutes = COALESCE(t.sla_response_target_minutes, p.response_time, 60),
    sla_resolution_target_minutes = COALESCE(t.sla_resolution_target_minutes, p.resolution_time, 480)
FROM sla_policies p
WHERE p.priority = t.priority
  AND (t.sla_response_target_minutes IS NULL OR t.sla_resolution_target_minutes IS NULL);

UPDATE tickets
SET sla_response_target_minutes = COALESCE(sla_response_target_minutes, 60),
    sla_resolution_target_minutes = COALESCE(sla_resolution_target_minutes, 480),
    sla_response_due_at = COALESCE(sla_response_due_at, created_at + COALESCE(sla_response_target_minutes, 60) * INTERVAL '1 minute'),
    sla_resolution_due_at = COALESCE(sla_resolution_due_at, created_at + COALESCE(sla_resolution_target_minutes, 480) * INTERVAL '1 minute');

UPDATE tickets t
SET sla_first_response_at = response.first_response_at
FROM (
  SELECT ticket_id, MIN(timestamp) AS first_response_at
  FROM ticket_history
  WHERE updated_by !~ '^user-[0-9]+$'
  GROUP BY ticket_id
) response
WHERE t.id = response.ticket_id AND t.sla_first_response_at IS NULL;

UPDATE tickets t
SET sla_resolved_at = terminal.resolved_at
FROM (
  SELECT ticket_id, MIN(timestamp) AS resolved_at
  FROM ticket_history
  WHERE status IN ('RESOLVED', 'CLOSED')
  GROUP BY ticket_id
) terminal
WHERE t.id = terminal.ticket_id AND t.sla_resolved_at IS NULL;

UPDATE tickets
SET sla_response_breached_at = COALESCE(sla_first_response_at, CURRENT_TIMESTAMP)
WHERE sla_response_breached_at IS NULL
  AND COALESCE(sla_first_response_at, CURRENT_TIMESTAMP) > sla_response_due_at;

UPDATE tickets
SET sla_resolution_breached_at = COALESCE(sla_resolved_at, CURRENT_TIMESTAMP)
WHERE sla_resolution_breached_at IS NULL
  AND COALESCE(sla_resolved_at, CURRENT_TIMESTAMP) > sla_resolution_due_at
  AND (sla_resolved_at IS NOT NULL OR status NOT IN ('RESOLVED', 'CLOSED'));

CREATE INDEX IF NOT EXISTS tickets_sla_response_due_idx ON tickets(sla_response_due_at);
CREATE INDEX IF NOT EXISTS tickets_sla_resolution_due_idx ON tickets(sla_resolution_due_at);

CREATE TABLE IF NOT EXISTS sla_risk_predictions (
  id BIGSERIAL PRIMARY KEY,
  ticket_id INTEGER NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
  score INTEGER NOT NULL,
  level VARCHAR(20) NOT NULL,
  reasons TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  UNIQUE(ticket_id, level)
);

CREATE INDEX IF NOT EXISTS sla_risk_predictions_ticket_idx ON sla_risk_predictions(ticket_id);
CREATE INDEX IF NOT EXISTS sla_risk_predictions_created_idx ON sla_risk_predictions(created_at);
