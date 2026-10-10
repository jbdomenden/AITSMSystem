-- Complete the production schema and bind persisted AI conversations to users.
-- Existing messages predate ownership metadata and are deliberately removed so
-- that conversation history can never be exposed across accounts.
BEGIN;

CREATE TABLE IF NOT EXISTS system_settings (
  id SERIAL PRIMARY KEY,
  key VARCHAR(120) NOT NULL UNIQUE,
  value TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS related_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS notifications_related_user_idx ON notifications(related_user_id);

CREATE TABLE IF NOT EXISTS ai_conversation_messages (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  session_id VARCHAR(128) NOT NULL,
  role VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);

ALTER TABLE ai_conversation_messages
  ADD COLUMN IF NOT EXISTS user_id INTEGER REFERENCES users(id) ON DELETE CASCADE;

DELETE FROM ai_conversation_messages WHERE user_id IS NULL;
ALTER TABLE ai_conversation_messages ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS ai_conversation_messages_owner_session_idx
  ON ai_conversation_messages(user_id, session_id, created_at);

COMMIT;
