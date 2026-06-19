-- pg_dump --schema-only from dev DB (privchat-application)
--
-- PostgreSQL database dump
--

-- Dumped from database version 16.9 (Homebrew)
-- Dumped by pg_dump version 16.9 (Homebrew)

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
-- Name: pay_apps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_apps (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_apps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_apps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_apps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_apps_id_seq OWNED BY public.pay_apps.id;


--
-- Name: pay_channels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_channels (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
    code character varying(64) NOT NULL,
    config text DEFAULT ''::text NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    fee_rate integer DEFAULT 0 NOT NULL,
    remark character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
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
-- Name: pay_notify_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_notify_tasks (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
    type smallint DEFAULT 0 NOT NULL,
    data_id bigint NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    merchant_url character varying(512),
    notify_times integer DEFAULT 0 NOT NULL,
    max_notify_times integer DEFAULT 0 NOT NULL,
    next_notify_time bigint,
    last_execute_time bigint,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: pay_notify_tasks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pay_notify_tasks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pay_notify_tasks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pay_notify_tasks_id_seq OWNED BY public.pay_notify_tasks.id;


--
-- Name: pay_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_orders (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
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
-- Name: pay_refunds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_refunds (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
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
-- Name: pay_transfers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pay_transfers (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
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
-- Name: pay_apps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_apps ALTER COLUMN id SET DEFAULT nextval('public.pay_apps_id_seq'::regclass);


--
-- Name: pay_channels id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_channels ALTER COLUMN id SET DEFAULT nextval('public.pay_channels_id_seq'::regclass);


--
-- Name: pay_notify_tasks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_notify_tasks ALTER COLUMN id SET DEFAULT nextval('public.pay_notify_tasks_id_seq'::regclass);


--
-- Name: pay_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_orders ALTER COLUMN id SET DEFAULT nextval('public.pay_orders_id_seq'::regclass);


--
-- Name: pay_refunds id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_refunds ALTER COLUMN id SET DEFAULT nextval('public.pay_refunds_id_seq'::regclass);


--
-- Name: pay_transfers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_transfers ALTER COLUMN id SET DEFAULT nextval('public.pay_transfers_id_seq'::regclass);


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
-- Name: pay_apps pay_apps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_apps
    ADD CONSTRAINT pay_apps_pkey PRIMARY KEY (id);


--
-- Name: pay_channels pay_channels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_channels
    ADD CONSTRAINT pay_channels_pkey PRIMARY KEY (id);


--
-- Name: pay_notify_tasks pay_notify_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_notify_tasks
    ADD CONSTRAINT pay_notify_tasks_pkey PRIMARY KEY (id);


--
-- Name: pay_orders pay_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_orders
    ADD CONSTRAINT pay_orders_pkey PRIMARY KEY (id);


--
-- Name: pay_refunds pay_refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_refunds
    ADD CONSTRAINT pay_refunds_pkey PRIMARY KEY (id);


--
-- Name: pay_transfers pay_transfers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pay_transfers
    ADD CONSTRAINT pay_transfers_pkey PRIMARY KEY (id);


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
-- Name: idx_pay_channels_app; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_channels_app ON public.pay_channels USING btree (app_id);


--
-- Name: idx_pay_orders_app; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_orders_app ON public.pay_orders USING btree (app_id);


--
-- Name: idx_pay_orders_merchant; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_pay_orders_merchant ON public.pay_orders USING btree (merchant_order_id);


--
-- Name: idx_pay_refunds_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pay_refunds_order ON public.pay_refunds USING btree (order_id);


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
-- PostgreSQL database dump complete
--

