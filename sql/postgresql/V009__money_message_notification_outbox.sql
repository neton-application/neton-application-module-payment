-- module-payment V009: Money Message 通知 outbox（RP-7-A 可靠通知底座）
-- payment 只产出通知事件（与资金动作同事务写入），module-privchat 后续 adapter/job
-- 读 PENDING 消费并调 PrivchatServiceClient 注入 IM notification。IM 失败可重试，
-- 不影响资金事务（资金真相在 payment，通知是可重试副作用）。金额 bigint(分)，时间 epoch ms。
SET search_path = public;

CREATE SEQUENCE IF NOT EXISTS public.pay_money_message_notification_outbox_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.pay_money_message_notification_outbox (
    id bigint NOT NULL DEFAULT nextval('public.pay_money_message_notification_outbox_id_seq'::regclass),
    -- RED_PACKET_RECEIVED / RED_PACKET_EMPTY / RED_PACKET_EXPIRED
    event_type character varying(32) NOT NULL,
    channel_id character varying(64) DEFAULT '' NOT NULL,
    scene smallint DEFAULT 0 NOT NULL,
    red_packet_id bigint DEFAULT 0 NOT NULL,
    -- RECEIVED: 领取人；EMPTY/EXPIRED: 发送人
    related_user_id bigint DEFAULT 0 NOT NULL,
    -- RECEIVED: 发送人；其它 0
    target_user_id bigint DEFAULT 0 NOT NULL,
    payload_json text NOT NULL,
    -- 0 PENDING / 1 SENT / 2 FAILED
    status smallint DEFAULT 0 NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    next_retry_at bigint DEFAULT 0 NOT NULL,
    last_error character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL,
    sent_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pay_money_message_notification_outbox_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.pay_money_message_notification_outbox_id_seq
    OWNED BY public.pay_money_message_notification_outbox.id;

-- adapter 拉取待发：status=PENDING 且到重试时间，按 id 升序（FIFO）。
CREATE INDEX IF NOT EXISTS idx_mmno_pending
    ON public.pay_money_message_notification_outbox (status, next_retry_at, id);
CREATE INDEX IF NOT EXISTS idx_mmno_red_packet
    ON public.pay_money_message_notification_outbox (red_packet_id, id);
