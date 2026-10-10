-- Repair accounts created before account_state was backfilled consistently.
-- A verified account cannot still be waiting for email verification.
BEGIN;

UPDATE users
SET account_state = 'ACTIVE'
WHERE email_verified = TRUE
  AND account_state = 'PENDING_VERIFICATION';

COMMIT;
