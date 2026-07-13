-- #85: money-message card delivery reliability.
--
-- Eliminate the dual-write (card injected via cross-service HTTP *inside* the
-- open payment transaction) and make the async delivery a production-grade
-- Transactional Outbox: worker lease (multi-instance safe claim), exponential
-- backoff, a terminal DEAD state (no more silent drop after N retries), and a
-- server_message_id for delivery evidence / detail queries / admin re-drive.
--
-- Status model (smallint, backward compatible with existing 0/1/2 rows):
--   0 PENDING      awaiting first delivery (or re-queued after a released lease)
--   1 SENT         delivered; server_message_id + sent_at set
--   2 RETRY_WAIT   transient failure; retry after next_retry_at (was "FAILED")
--   3 PROCESSING   claimed by a worker; lease_owner holds it until lease_expires_at
--   4 DEAD         retries exhausted; needs admin re-drive (never silently dropped)

ALTER TABLE pay_money_message_notification_outbox ADD COLUMN IF NOT EXISTS server_message_id bigint;
ALTER TABLE pay_money_message_notification_outbox ADD COLUMN IF NOT EXISTS processing_started_at bigint NOT NULL DEFAULT 0;
ALTER TABLE pay_money_message_notification_outbox ADD COLUMN IF NOT EXISTS lease_owner varchar(64);
ALTER TABLE pay_money_message_notification_outbox ADD COLUMN IF NOT EXISTS lease_expires_at bigint NOT NULL DEFAULT 0;
ALTER TABLE pay_money_message_notification_outbox ADD COLUMN IF NOT EXISTS dead_at bigint NOT NULL DEFAULT 0;

-- Recover leases abandoned by a crashed worker: find PROCESSING rows whose lease expired.
CREATE INDEX IF NOT EXISTS idx_mmno_lease
    ON pay_money_message_notification_outbox (status, lease_expires_at)
    WHERE status = 3;

-- Monitor / admin-list the dead-letter backlog.
CREATE INDEX IF NOT EXISTS idx_mmno_dead
    ON pay_money_message_notification_outbox (status, dead_at)
    WHERE status = 4;
