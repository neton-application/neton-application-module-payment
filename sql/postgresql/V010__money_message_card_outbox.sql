-- RP-12: the money-message outbox now also carries CARD injection events
-- (RED_PACKET_CARD / MONEY_TRANSFER_CARD), not just RED_PACKET_* notifications.
-- Add a generic ref (ref_type + ref_id) so transfer cards fit too (the table
-- only had red_packet_id), plus a unique key so a card is enqueued exactly once
-- per order (payment ON CONFLICT DO NOTHING). Legacy notification rows keep
-- using red_packet_id and leave ref_id NULL (excluded from the partial index).
ALTER TABLE pay_money_message_notification_outbox ADD COLUMN IF NOT EXISTS ref_type varchar(16);
ALTER TABLE pay_money_message_notification_outbox ADD COLUMN IF NOT EXISTS ref_id bigint;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_mmno_card_dedup
    ON pay_money_message_notification_outbox (event_type, ref_type, ref_id)
    WHERE ref_id IS NOT NULL;
