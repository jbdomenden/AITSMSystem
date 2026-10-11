ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_key VARCHAR(180);

-- Direct messages use direct_messages.read_at and never belong in the transaction bell.
DELETE FROM notifications WHERE type = 'direct_message';

CREATE UNIQUE INDEX IF NOT EXISTS notifications_event_key_uidx
    ON notifications(event_key) WHERE event_key IS NOT NULL;
