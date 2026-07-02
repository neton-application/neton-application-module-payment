-- module-payment V008: 红包 + 转账（PrivChat Money Message, RP-2）
-- 红包发即扣全额进托管(remaining_amount 为真相)，领取减 remaining，过期退剩余。
-- 转账无需接收确认，一事务内扣发送方 + 入账接收方。金额 bigint(分)，时间 epoch ms。
-- ledger biz_type：400 红包扣款 / 401 红包领取入账 / 402 红包退款 / 500 转出 / 501 转入 / 502 转账退款。
SET search_path = public;

-- ============ 红包订单 ============
CREATE SEQUENCE IF NOT EXISTS public.red_packet_orders_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.red_packet_orders (
    id bigint NOT NULL DEFAULT nextval('public.red_packet_orders_id_seq'::regclass),
    sender_user_id bigint NOT NULL,
    channel_id character varying(64) DEFAULT '' NOT NULL,
    scene smallint DEFAULT 0 NOT NULL,
    type smallint DEFAULT 0 NOT NULL,
    total_amount bigint NOT NULL,
    total_count integer NOT NULL,
    remaining_amount bigint NOT NULL,
    remaining_count integer NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    greeting character varying(255),
    expire_at bigint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    finished_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT red_packet_orders_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.red_packet_orders_id_seq OWNED BY public.red_packet_orders.id;

CREATE INDEX IF NOT EXISTS idx_red_packet_orders_sender
    ON public.red_packet_orders (sender_user_id, id DESC);
-- 过期扫描：ACTIVE 且已过期的红包
CREATE INDEX IF NOT EXISTS idx_red_packet_orders_expire
    ON public.red_packet_orders (status, expire_at);

-- ============ 红包领取记录 ============
CREATE SEQUENCE IF NOT EXISTS public.red_packet_claims_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.red_packet_claims (
    id bigint NOT NULL DEFAULT nextval('public.red_packet_claims_id_seq'::regclass),
    red_packet_id bigint NOT NULL,
    user_id bigint NOT NULL,
    amount bigint NOT NULL,
    claimed_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT red_packet_claims_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.red_packet_claims_id_seq OWNED BY public.red_packet_claims.id;

-- 防重复领取（同一红包同一用户仅一条）——RP-3 并发防超领双闸之一。
CREATE UNIQUE INDEX IF NOT EXISTS uq_red_packet_claims_rp_user
    ON public.red_packet_claims (red_packet_id, user_id);

-- ============ 转账订单 ============
CREATE SEQUENCE IF NOT EXISTS public.money_transfer_orders_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.money_transfer_orders (
    id bigint NOT NULL DEFAULT nextval('public.money_transfer_orders_id_seq'::regclass),
    from_user_id bigint NOT NULL,
    to_user_id bigint NOT NULL,
    channel_id character varying(64) DEFAULT '' NOT NULL,
    amount bigint NOT NULL,
    remark character varying(255),
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT money_transfer_orders_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.money_transfer_orders_id_seq OWNED BY public.money_transfer_orders.id;

CREATE INDEX IF NOT EXISTS idx_money_transfer_orders_from
    ON public.money_transfer_orders (from_user_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_money_transfer_orders_to
    ON public.money_transfer_orders (to_user_id, id DESC);

-- ============ ledger 幂等扩展 ============
-- 复用 V003 的 (biz_type, biz_id) 部分唯一索引思路，覆盖红包/转账 biz_type。
-- 红包扣款/退款 biz_id=red_packet_id；红包领取 biz_id=claim_id；转账各段 biz_id=transfer_id。
CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_wallet_tx_money_msg_idem
    ON public.pay_wallet_transactions (biz_type, biz_id)
    WHERE biz_type IN (400, 401, 402, 500, 501, 502);
