-- module-payment V005: 提现订单 + 审批审计（P4-C）
-- 申请提现只冻结资金（pay_wallets.freeze_price += amount，见 P4-B0），不扣 balance；
-- PAID 才从冻结实扣。状态见 logic.WithdrawStateMachine。金额 bigint(分)。
SET search_path = public;

-- ============ 提现订单 ============
CREATE SEQUENCE IF NOT EXISTS public.wallet_withdraw_orders_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.wallet_withdraw_orders (
    id bigint NOT NULL DEFAULT nextval('public.wallet_withdraw_orders_id_seq'::regclass),
    user_id bigint NOT NULL,
    wallet_id bigint NOT NULL,
    bank_card_id bigint NOT NULL,
    amount bigint NOT NULL,
    fee bigint DEFAULT 0 NOT NULL,
    actual_amount bigint NOT NULL,
    currency character varying(8) DEFAULT 'CNY' NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    reviewer_id bigint DEFAULT 0 NOT NULL,
    review_remark character varying(512),
    freeze_remark_user_visible character varying(512),
    failure_reason character varying(512),
    payment_channel_id bigint DEFAULT 0 NOT NULL,
    payout_channel character varying(64),
    payout_trade_no character varying(128),
    created_at bigint DEFAULT 0 NOT NULL,
    reviewed_at bigint DEFAULT 0 NOT NULL,
    paid_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT wallet_withdraw_orders_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.wallet_withdraw_orders_id_seq OWNED BY public.wallet_withdraw_orders.id;

CREATE INDEX IF NOT EXISTS idx_wallet_withdraw_orders_user
    ON public.wallet_withdraw_orders (user_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_withdraw_orders_status
    ON public.wallet_withdraw_orders (status, id DESC);

-- ============ 审批审计 ============
CREATE SEQUENCE IF NOT EXISTS public.wallet_withdraw_audit_logs_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.wallet_withdraw_audit_logs (
    id bigint NOT NULL DEFAULT nextval('public.wallet_withdraw_audit_logs_id_seq'::regclass),
    order_id bigint NOT NULL,
    operator_id bigint NOT NULL,
    action character varying(32) NOT NULL,
    before_status smallint NOT NULL,
    after_status smallint NOT NULL,
    remark character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT wallet_withdraw_audit_logs_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.wallet_withdraw_audit_logs_id_seq OWNED BY public.wallet_withdraw_audit_logs.id;

CREATE INDEX IF NOT EXISTS idx_wallet_withdraw_audit_order
    ON public.wallet_withdraw_audit_logs (order_id, id);
