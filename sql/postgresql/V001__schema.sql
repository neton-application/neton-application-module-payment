-- payment: 1.0.0-beta fresh-install schema. Not an upgrade path.
-- Apply through the migration runner; never edit after this baseline is released.
SET search_path = public;
--
--


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: pay_channels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_channels (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    config text DEFAULT ''::text NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    fee_rate integer DEFAULT 0 NOT NULL,
    remark character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL,
    platform_code character varying(64) DEFAULT ''::character varying NOT NULL,
    method character varying(32) DEFAULT 'OTHER'::character varying NOT NULL,
    platform_channel_id character varying(128),
    display_mode character varying(32) DEFAULT 'REDIRECT_URL'::character varying NOT NULL
);


--
-- Name: pay_channels_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_channels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_channels_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_channels_id_seq OWNED BY public.pay_channels.id;


--
-- Name: pay_money_message_notification_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_money_message_notification_outbox (
    id bigint NOT NULL,
    event_type character varying(32) NOT NULL,
    channel_id character varying(64) DEFAULT ''::character varying NOT NULL,
    scene smallint DEFAULT 0 NOT NULL,
    red_packet_id bigint DEFAULT 0 NOT NULL,
    related_user_id bigint DEFAULT 0 NOT NULL,
    target_user_id bigint DEFAULT 0 NOT NULL,
    payload_json text NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    next_retry_at bigint DEFAULT 0 NOT NULL,
    last_error character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL,
    sent_at bigint DEFAULT 0 NOT NULL,
    ref_type character varying(16),
    ref_id bigint,
    server_message_id bigint,
    processing_started_at bigint DEFAULT 0 NOT NULL,
    lease_owner character varying(64),
    lease_expires_at bigint DEFAULT 0 NOT NULL,
    dead_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_money_message_notification_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_money_message_notification_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_money_message_notification_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_money_message_notification_outbox_id_seq OWNED BY public.pay_money_message_notification_outbox.id;


--
-- Name: pay_money_transfer_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_money_transfer_orders (
    id bigint NOT NULL,
    from_user_id bigint NOT NULL,
    to_user_id bigint NOT NULL,
    channel_id character varying(64) DEFAULT ''::character varying NOT NULL,
    amount bigint NOT NULL,
    remark character varying(255),
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_money_transfer_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_money_transfer_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_money_transfer_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_money_transfer_orders_id_seq OWNED BY public.pay_money_transfer_orders.id;


--
-- Name: pay_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_orders (
    id bigint NOT NULL,
    merchant_order_id character varying(128) NOT NULL,
    subject character varying(255) NOT NULL,
    body text,
    price bigint DEFAULT 0 NOT NULL,
    channel_code character varying(64),
    channel_order_no character varying(128),
    status smallint DEFAULT 0 NOT NULL,
    user_ip character varying(64),
    expire_time bigint,
    success_time bigint,
    notify_time bigint,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_orders_id_seq OWNED BY public.pay_orders.id;


--
-- Name: pay_red_packet_claims; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_red_packet_claims (
    id bigint NOT NULL,
    red_packet_id bigint NOT NULL,
    user_id bigint NOT NULL,
    amount bigint NOT NULL,
    claimed_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_red_packet_claims_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_red_packet_claims_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_red_packet_claims_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_red_packet_claims_id_seq OWNED BY public.pay_red_packet_claims.id;


--
-- Name: pay_red_packet_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_red_packet_orders (
    id bigint NOT NULL,
    sender_user_id bigint NOT NULL,
    channel_id character varying(64) DEFAULT ''::character varying NOT NULL,
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
    finished_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_red_packet_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_red_packet_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_red_packet_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_red_packet_orders_id_seq OWNED BY public.pay_red_packet_orders.id;


--
-- Name: pay_refunds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_refunds (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    merchant_refund_id character varying(128) NOT NULL,
    channel_code character varying(64),
    channel_refund_no character varying(128),
    pay_price bigint DEFAULT 0 NOT NULL,
    refund_price bigint DEFAULT 0 NOT NULL,
    reason character varying(512),
    status smallint DEFAULT 0 NOT NULL,
    success_time bigint,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_refunds_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_refunds_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_refunds_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_refunds_id_seq OWNED BY public.pay_refunds.id;


--
-- Name: pay_sensitive_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_sensitive_audit_logs (
    id bigint NOT NULL,
    operator_id bigint DEFAULT 0 NOT NULL,
    operator_name character varying(128),
    operator_role character varying(256),
    action character varying(64) NOT NULL,
    target_type character varying(64) NOT NULL,
    target_id bigint DEFAULT 0 NOT NULL,
    target_user_id bigint DEFAULT 0 NOT NULL,
    ip character varying(64),
    user_agent character varying(512),
    trace_id character varying(64),
    before_snapshot text,
    after_snapshot text,
    reason text,
    created_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_sensitive_audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_sensitive_audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_sensitive_audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_sensitive_audit_logs_id_seq OWNED BY public.pay_sensitive_audit_logs.id;


--
-- Name: pay_transfers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_transfers (
    id bigint NOT NULL,
    channel_code character varying(64),
    merchant_transfer_id character varying(128) NOT NULL,
    type smallint DEFAULT 0 NOT NULL,
    price bigint DEFAULT 0 NOT NULL,
    subject character varying(255) NOT NULL,
    user_name character varying(64),
    account_no character varying(128),
    status smallint DEFAULT 0 NOT NULL,
    success_time bigint,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_transfers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_transfers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_transfers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_transfers_id_seq OWNED BY public.pay_transfers.id;


--
-- Name: pay_wallet_freezes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_wallet_freezes (
    id bigint NOT NULL,
    wallet_id bigint NOT NULL,
    user_id bigint NOT NULL,
    freeze_type smallint NOT NULL,
    amount bigint,
    status smallint DEFAULT 0 NOT NULL,
    ref_type smallint NOT NULL,
    ref_id character varying(128) NOT NULL,
    reason_code character varying(64),
    reason_text text,
    operator_id bigint DEFAULT 0 NOT NULL,
    expires_at bigint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    released_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_wallet_freezes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_wallet_freezes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_wallet_freezes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_wallet_freezes_id_seq OWNED BY public.pay_wallet_freezes.id;


--
-- Name: pay_wallet_recharge_packages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_wallet_recharge_packages (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    pay_price bigint DEFAULT 0 NOT NULL,
    bonus_price bigint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_wallet_recharge_packages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_wallet_recharge_packages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_wallet_recharge_packages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_wallet_recharge_packages_id_seq OWNED BY public.pay_wallet_recharge_packages.id;


--
-- Name: pay_wallet_recharges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_wallet_recharges (
    id bigint NOT NULL,
    wallet_id bigint NOT NULL,
    total_price bigint DEFAULT 0 NOT NULL,
    pay_price bigint DEFAULT 0 NOT NULL,
    bonus_price bigint DEFAULT 0 NOT NULL,
    package_id bigint,
    pay_status smallint DEFAULT 0 NOT NULL,
    pay_order_id bigint,
    pay_channel_code character varying(64),
    refund_status smallint DEFAULT 0 NOT NULL,
    refund_total_price bigint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_wallet_recharges_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_wallet_recharges_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_wallet_recharges_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_wallet_recharges_id_seq OWNED BY public.pay_wallet_recharges.id;


--
-- Name: pay_wallet_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_wallet_transactions (
    id bigint NOT NULL,
    wallet_id bigint NOT NULL,
    biz_type smallint DEFAULT 0 NOT NULL,
    biz_id bigint DEFAULT 0 NOT NULL,
    title character varying(128) NOT NULL,
    price bigint DEFAULT 0 NOT NULL,
    balance bigint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_wallet_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_wallet_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_wallet_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_wallet_transactions_id_seq OWNED BY public.pay_wallet_transactions.id;


--
-- Name: pay_wallets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_wallets (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    balance bigint DEFAULT 0 NOT NULL,
    total_expense bigint DEFAULT 0 NOT NULL,
    total_recharge bigint DEFAULT 0 NOT NULL,
    freeze_price bigint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_wallets_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_wallets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_wallets_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_wallets_id_seq OWNED BY public.pay_wallets.id;


--
-- Name: user_bank_cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_bank_cards (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    holder_name character varying(64) NOT NULL,
    bank_name character varying(128) NOT NULL,
    bank_code character varying(64),
    card_no_ciphertext text NOT NULL,
    card_no_nonce text,
    encrypted_data_key text NOT NULL,
    card_no_masked character varying(32) NOT NULL,
    card_no_hash character varying(128) NOT NULL,
    encryption_version integer DEFAULT 1 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL,
    deleted_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: user_bank_cards_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_bank_cards_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_bank_cards_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_bank_cards_id_seq OWNED BY public.user_bank_cards.id;


--
-- Name: wallet_withdraw_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wallet_withdraw_audit_logs (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    operator_id bigint NOT NULL,
    action character varying(32) NOT NULL,
    before_status smallint NOT NULL,
    after_status smallint NOT NULL,
    remark character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    operator_name character varying(128),
    operator_role character varying(256),
    ip character varying(64),
    user_agent character varying(512),
    trace_id character varying(64)
);


--
-- Name: wallet_withdraw_audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.wallet_withdraw_audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: wallet_withdraw_audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.wallet_withdraw_audit_logs_id_seq OWNED BY public.wallet_withdraw_audit_logs.id;


--
-- Name: wallet_withdraw_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wallet_withdraw_orders (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    wallet_id bigint NOT NULL,
    bank_card_id bigint NOT NULL,
    amount bigint NOT NULL,
    fee bigint DEFAULT 0 NOT NULL,
    actual_amount bigint NOT NULL,
    currency character varying(8) DEFAULT 'CNY'::character varying NOT NULL,
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
    hold_resume_to integer DEFAULT 0 NOT NULL,
    hold_note_internal text,
    hold_at bigint DEFAULT 0 NOT NULL,
    hold_by bigint DEFAULT 0 NOT NULL,
    hold_reason_text text
);


--
-- Name: COLUMN wallet_withdraw_orders.hold_reason_text; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.wallet_withdraw_orders.hold_reason_text IS '挂起原因，运营手填，原样展示给用户。不做 i18n。';


--
-- Name: wallet_withdraw_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.wallet_withdraw_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: wallet_withdraw_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.wallet_withdraw_orders_id_seq OWNED BY public.wallet_withdraw_orders.id;


--
-- Name: pay_channels id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_channels ALTER COLUMN id SET DEFAULT nextval('public.pay_channels_id_seq'::regclass);


--
-- Name: pay_money_message_notification_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_money_message_notification_outbox ALTER COLUMN id SET DEFAULT nextval('public.pay_money_message_notification_outbox_id_seq'::regclass);


--
-- Name: pay_money_transfer_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_money_transfer_orders ALTER COLUMN id SET DEFAULT nextval('public.pay_money_transfer_orders_id_seq'::regclass);


--
-- Name: pay_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_orders ALTER COLUMN id SET DEFAULT nextval('public.pay_orders_id_seq'::regclass);


--
-- Name: pay_red_packet_claims id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_red_packet_claims ALTER COLUMN id SET DEFAULT nextval('public.pay_red_packet_claims_id_seq'::regclass);


--
-- Name: pay_red_packet_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_red_packet_orders ALTER COLUMN id SET DEFAULT nextval('public.pay_red_packet_orders_id_seq'::regclass);


--
-- Name: pay_refunds id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_refunds ALTER COLUMN id SET DEFAULT nextval('public.pay_refunds_id_seq'::regclass);


--
-- Name: pay_sensitive_audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_sensitive_audit_logs ALTER COLUMN id SET DEFAULT nextval('public.pay_sensitive_audit_logs_id_seq'::regclass);


--
-- Name: pay_transfers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_transfers ALTER COLUMN id SET DEFAULT nextval('public.pay_transfers_id_seq'::regclass);


--
-- Name: pay_wallet_freezes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallet_freezes ALTER COLUMN id SET DEFAULT nextval('public.pay_wallet_freezes_id_seq'::regclass);


--
-- Name: pay_wallet_recharge_packages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallet_recharge_packages ALTER COLUMN id SET DEFAULT nextval('public.pay_wallet_recharge_packages_id_seq'::regclass);


--
-- Name: pay_wallet_recharges id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallet_recharges ALTER COLUMN id SET DEFAULT nextval('public.pay_wallet_recharges_id_seq'::regclass);


--
-- Name: pay_wallet_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallet_transactions ALTER COLUMN id SET DEFAULT nextval('public.pay_wallet_transactions_id_seq'::regclass);


--
-- Name: pay_wallets id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallets ALTER COLUMN id SET DEFAULT nextval('public.pay_wallets_id_seq'::regclass);


--
-- Name: user_bank_cards id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_bank_cards ALTER COLUMN id SET DEFAULT nextval('public.user_bank_cards_id_seq'::regclass);


--
-- Name: wallet_withdraw_audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_withdraw_audit_logs ALTER COLUMN id SET DEFAULT nextval('public.wallet_withdraw_audit_logs_id_seq'::regclass);


--
-- Name: wallet_withdraw_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_withdraw_orders ALTER COLUMN id SET DEFAULT nextval('public.wallet_withdraw_orders_id_seq'::regclass);


--
-- Name: pay_channels pay_channels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_channels
    ADD CONSTRAINT pay_channels_pkey PRIMARY KEY (id);


--
-- Name: pay_money_message_notification_outbox pay_money_message_notification_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_money_message_notification_outbox
    ADD CONSTRAINT pay_money_message_notification_outbox_pkey PRIMARY KEY (id);


--
-- Name: pay_money_transfer_orders pay_money_transfer_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_money_transfer_orders
    ADD CONSTRAINT pay_money_transfer_orders_pkey PRIMARY KEY (id);


--
-- Name: pay_orders pay_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_orders
    ADD CONSTRAINT pay_orders_pkey PRIMARY KEY (id);


--
-- Name: pay_red_packet_claims pay_red_packet_claims_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_red_packet_claims
    ADD CONSTRAINT pay_red_packet_claims_pkey PRIMARY KEY (id);


--
-- Name: pay_red_packet_orders pay_red_packet_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_red_packet_orders
    ADD CONSTRAINT pay_red_packet_orders_pkey PRIMARY KEY (id);


--
-- Name: pay_refunds pay_refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_refunds
    ADD CONSTRAINT pay_refunds_pkey PRIMARY KEY (id);


--
-- Name: pay_sensitive_audit_logs pay_sensitive_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_sensitive_audit_logs
    ADD CONSTRAINT pay_sensitive_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: pay_transfers pay_transfers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_transfers
    ADD CONSTRAINT pay_transfers_pkey PRIMARY KEY (id);


--
-- Name: pay_wallet_freezes pay_wallet_freezes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallet_freezes
    ADD CONSTRAINT pay_wallet_freezes_pkey PRIMARY KEY (id);


--
-- Name: pay_wallet_recharge_packages pay_wallet_recharge_packages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallet_recharge_packages
    ADD CONSTRAINT pay_wallet_recharge_packages_pkey PRIMARY KEY (id);


--
-- Name: pay_wallet_recharges pay_wallet_recharges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallet_recharges
    ADD CONSTRAINT pay_wallet_recharges_pkey PRIMARY KEY (id);


--
-- Name: pay_wallet_transactions pay_wallet_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallet_transactions
    ADD CONSTRAINT pay_wallet_transactions_pkey PRIMARY KEY (id);


--
-- Name: pay_wallets pay_wallets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_wallets
    ADD CONSTRAINT pay_wallets_pkey PRIMARY KEY (id);


--
-- Name: user_bank_cards user_bank_cards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_bank_cards
    ADD CONSTRAINT user_bank_cards_pkey PRIMARY KEY (id);


--
-- Name: wallet_withdraw_audit_logs wallet_withdraw_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_withdraw_audit_logs
    ADD CONSTRAINT wallet_withdraw_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: wallet_withdraw_orders wallet_withdraw_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_withdraw_orders
    ADD CONSTRAINT wallet_withdraw_orders_pkey PRIMARY KEY (id);


--
-- Name: idx_mmno_dead; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mmno_dead ON public.pay_money_message_notification_outbox USING btree (status, dead_at) WHERE (status = 4);


--
-- Name: idx_mmno_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mmno_lease ON public.pay_money_message_notification_outbox USING btree (status, lease_expires_at) WHERE (status = 3);


--
-- Name: idx_mmno_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mmno_pending ON public.pay_money_message_notification_outbox USING btree (status, next_retry_at, id);


--
-- Name: idx_mmno_red_packet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mmno_red_packet ON public.pay_money_message_notification_outbox USING btree (red_packet_id, id);


--
-- Name: idx_pay_channels_platform; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_channels_platform ON public.pay_channels USING btree (platform_code, status);


--
-- Name: idx_pay_money_transfer_orders_from; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_money_transfer_orders_from ON public.pay_money_transfer_orders USING btree (from_user_id, id DESC);


--
-- Name: idx_pay_money_transfer_orders_to; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_money_transfer_orders_to ON public.pay_money_transfer_orders USING btree (to_user_id, id DESC);


--
-- Name: idx_pay_orders_merchant; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_pay_orders_merchant ON public.pay_orders USING btree (merchant_order_id);


--
-- Name: idx_pay_red_packet_orders_expire; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_red_packet_orders_expire ON public.pay_red_packet_orders USING btree (status, expire_at);


--
-- Name: idx_pay_red_packet_orders_sender; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_red_packet_orders_sender ON public.pay_red_packet_orders USING btree (sender_user_id, id DESC);


--
-- Name: idx_pay_refunds_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_refunds_order ON public.pay_refunds USING btree (order_id);


--
-- Name: idx_pay_sensitive_audit_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_sensitive_audit_action ON public.pay_sensitive_audit_logs USING btree (action, id);


--
-- Name: idx_pay_sensitive_audit_operator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_sensitive_audit_operator ON public.pay_sensitive_audit_logs USING btree (operator_id, id);


--
-- Name: idx_pay_sensitive_audit_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_sensitive_audit_target ON public.pay_sensitive_audit_logs USING btree (target_type, target_id);


--
-- Name: idx_pay_wallet_recharges_wallet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_wallet_recharges_wallet ON public.pay_wallet_recharges USING btree (wallet_id);


--
-- Name: idx_pay_wallet_transactions_wallet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_wallet_transactions_wallet ON public.pay_wallet_transactions USING btree (wallet_id);


--
-- Name: idx_pay_wallets_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_pay_wallets_user ON public.pay_wallets USING btree (user_id);


--
-- Name: idx_user_bank_cards_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_bank_cards_user ON public.user_bank_cards USING btree (user_id) WHERE (deleted_at = 0);


--
-- Name: idx_wallet_freeze_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wallet_freeze_active ON public.pay_wallet_freezes USING btree (wallet_id, freeze_type) WHERE (status = 0);


--
-- Name: idx_wallet_freeze_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wallet_freeze_user ON public.pay_wallet_freezes USING btree (user_id, created_at DESC);


--
-- Name: idx_wallet_withdraw_audit_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wallet_withdraw_audit_order ON public.wallet_withdraw_audit_logs USING btree (order_id, id);


--
-- Name: idx_wallet_withdraw_orders_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wallet_withdraw_orders_status ON public.wallet_withdraw_orders USING btree (status, id DESC);


--
-- Name: idx_wallet_withdraw_orders_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wallet_withdraw_orders_user ON public.wallet_withdraw_orders USING btree (user_id, id DESC);


--
-- Name: idx_withdraw_on_hold; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_withdraw_on_hold ON public.wallet_withdraw_orders USING btree (status, hold_at DESC) WHERE (status = 7);


--
-- Name: uk_pay_channels_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_pay_channels_code ON public.pay_channels USING btree (code);


--
-- Name: uq_pay_mmno_card_dedup; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pay_mmno_card_dedup ON public.pay_money_message_notification_outbox USING btree (event_type, ref_type, ref_id) WHERE (ref_id IS NOT NULL);


--
-- Name: uq_pay_red_packet_claims_rp_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pay_red_packet_claims_rp_user ON public.pay_red_packet_claims USING btree (red_packet_id, user_id);


--
-- Name: uq_pay_wallet_tx_money_msg_idem; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pay_wallet_tx_money_msg_idem ON public.pay_wallet_transactions USING btree (biz_type, biz_id) WHERE (biz_type = ANY (ARRAY[400, 401, 402, 500, 501, 502]));


--
-- Name: uq_pay_wallet_tx_withdraw_idem; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pay_wallet_tx_withdraw_idem ON public.pay_wallet_transactions USING btree (biz_type, biz_id) WHERE (biz_type = ANY (ARRAY[300, 301, 302, 303]));


--
-- Name: uq_user_bank_cards_user_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_user_bank_cards_user_hash ON public.user_bank_cards USING btree (user_id, card_no_hash) WHERE (deleted_at = 0);


--
-- Name: uq_wallet_freeze_one_active_judicial; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_wallet_freeze_one_active_judicial ON public.pay_wallet_freezes USING btree (wallet_id) WHERE ((freeze_type = 3) AND (status = 0));


--
-- Name: uq_wallet_freeze_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_wallet_freeze_ref ON public.pay_wallet_freezes USING btree (freeze_type, ref_type, ref_id);


--
--


SET search_path = public;
