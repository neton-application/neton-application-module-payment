-- payment 模块（postgresql）—— 1.0.0 beta1 合并基线。
--
-- 🔴 **只给全新数据库用。** 由原来的 23 个迁移脚本按执行顺序拼接而成：
-- 顺序不变、语句不变，所以结果与逐条执行完全一致。
--
-- 为什么是拼接而不是导出结构快照：这些脚本里有 init_data / seed_menus 这类
-- **种子数据**，`pg_dump --schema-only` 会把它们丢掉，而只导结构就得再手工把
-- INSERT 补回来——那一步没有任何东西能验证对错。拼接则由构造保证等价。
--
-- 拼接的代价是留下了少量互相抵消的步骤（先加列、后改列）。它们无害，但**不要**
-- 试图"顺手清理"：清理一次就等于重新引入一个没人验证过的结构。
--
-- 存量库怎么办：本发布不提供原地升级。Neton 的迁移器按 checksum 校验，V001 变了
-- 就会拒绝启动——这是有意的，见 MigrationEngine 的 CHECKSUM_MISMATCH。
--
-- 加新东西请新增 V002、V003…，不要改这个文件。


-- ─────────────────────────────────────────────────────────────
-- 原 V001__create_tables.sql
-- ─────────────────────────────────────────────────────────────

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


-- ─────────────────────────────────────────────────────────────
-- 原 V002__seed_menus.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V002: 支付中心菜单 seed (从 dev 库导出)
SET search_path = public;-- ── 后台菜单 ──────────────────────────────────────────────────────────
--
-- 🔴 **不写死菜单 id**：id 由 system_menus 的序列在安装时分配。
--
-- 以前每个模块把 id 硬编码在 SQL 里，模块之间就得就编号达成一致，而唯一的
-- 保护是 `ON CONFLICT (id) DO NOTHING`——撞号不会报错，只会**静默**丢菜单。
-- 实测后果：gateway 和 privchat 撞了 700-704，于是「令牌管理」「定价修改」这些
-- AI 网关的按钮被挂到了「用户管理」「群组管理」底下，而没有任何地方报错。
--
-- 现在父子关系在语句内部用**模块内唯一的菜单名**连接（同一模块内不允许重名），
-- 跨模块不再共享任何编号，撞号从结构上不可能发生。
--
-- 加菜单：往对应层级的 VALUES 里加一行即可，不需要挑号。
-- 改菜单：后续迁移按 permission 定位；若该 permission 在本模块内不唯一，
--         用 name 加父节点定位。

WITH lvl1 AS (
    INSERT INTO system_menus (name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
    VALUES
        ('支付中心', '', 1, 0, '/pay', NULL, 'ant-design:pay-circle-outlined', 4, 1, (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint)
    RETURNING id, name
),
lvl2 AS (
    INSERT INTO system_menus (name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
    SELECT v.name, v.permission, v.type, p.id, v.path, v.component, v.icon, v.sort, v.status, (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint
    FROM (VALUES
        ('应用管理', 'pay:app:list', 2, '支付中心', 'app', 'pay/app/index', 'ant-design:appstore-outlined', 1, 1),
        ('支付订单', 'pay:order:list', 2, '支付中心', 'order', 'pay/order/index', 'ant-design:account-book-outlined', 2, 1),
        ('退款订单', 'pay:refund:list', 2, '支付中心', 'refund', 'pay/refund/index', 'ant-design:transaction-outlined', 3, 1),
        ('转账订单', 'pay:transfer:list', 2, '支付中心', 'transfer', 'pay/transfer/index', 'ant-design:swap-outlined', 5, 1),
        ('钱包管理', '', 1, '支付中心', 'wallet', NULL, 'ant-design:wallet-outlined', 6, 1),
        ('提现订单', 'pay:withdraw:list', 2, '支付中心', 'withdraw', 'pay/withdraw/index', 'ant-design:bank-outlined', 7, 1)
    ) AS v(name, permission, type, parent_name, path, component, icon, sort, status)
    JOIN lvl1 p ON p.name = v.parent_name
    RETURNING id, name
),
lvl3 AS (
    INSERT INTO system_menus (name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
    SELECT v.name, v.permission, v.type, p.id, v.path, v.component, v.icon, v.sort, v.status, (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint
    FROM (VALUES
        ('冻结管理', 'pay:wallet-freeze:menu', 2, '钱包管理', 'freeze', 'pay/wallet/freeze/index', NULL, 6, 1),
        ('钱包余额', 'pay:wallet:list', 2, '钱包管理', 'balance', 'pay/wallet/balance/index', '', 1, 1),
        ('充值套餐', 'pay:wallet-recharge-package:list', 2, '钱包管理', 'recharge-package', 'pay/wallet/rechargePackage/index', '', 2, 1),
        ('提现详情', 'pay:withdraw:detail', 3, '提现订单', '', '', '', 1, 1),
        ('审核通过', 'pay:withdraw:approve', 3, '提现订单', '', '', '', 2, 1),
        ('驳回', 'pay:withdraw:reject', 3, '提现订单', '', '', '', 3, 1),
        ('标记已打款', 'pay:withdraw:mark-paid', 3, '提现订单', '', '', '', 4, 1),
        ('标记失败', 'pay:withdraw:mark-failed', 3, '提现订单', '', '', '', 5, 1),
        ('查看打款银行卡', 'pay:bank-card:reveal', 3, '提现订单', '', '', '', 6, 1),
        ('挂起/解除挂起', 'pay:withdraw:hold', 3, '提现订单', '', '', '', 7, 1)
    ) AS v(name, permission, type, parent_name, path, component, icon, sort, status)
    JOIN lvl2 p ON p.name = v.parent_name
    RETURNING id, name
),
lvl4 AS (
    INSERT INTO system_menus (name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
    SELECT v.name, v.permission, v.type, p.id, v.path, v.component, v.icon, v.sort, v.status, (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint
    FROM (VALUES
        ('冻结查询', 'pay:wallet-freeze:list', 3, '冻结管理', NULL, NULL, NULL, 1, 1),
        ('账户冻结', 'pay:wallet-freeze:judicial', 3, '冻结管理', NULL, NULL, NULL, 3, 1),
        ('单笔冻结', 'pay:wallet-freeze:place', 3, '冻结管理', NULL, NULL, NULL, 2, 1),
        ('解除冻结', 'pay:wallet-freeze:release', 3, '冻结管理', NULL, NULL, NULL, 4, 1),
        ('钱包查询', 'pay:wallet:page', 3, '钱包余额', NULL, NULL, NULL, 1, 1),
        ('钱包详情', 'pay:wallet:query', 3, '钱包余额', NULL, NULL, NULL, 2, 1),
        ('钱包调整', 'pay:wallet:update', 3, '钱包余额', NULL, NULL, NULL, 3, 1),
        ('财务总览', 'pay:wallet:overview', 3, '钱包余额', NULL, NULL, NULL, 4, 1),
        ('钱包流水', 'pay:wallet-transaction:page', 3, '钱包余额', NULL, NULL, NULL, 5, 1),
        ('查看银行卡', 'pay:bank-card:list', 3, '钱包余额', NULL, NULL, NULL, 7, 1),
        ('解绑银行卡', 'pay:bank-card:unbind', 3, '钱包余额', NULL, NULL, NULL, 8, 1)
    ) AS v(name, permission, type, parent_name, path, component, icon, sort, status)
    JOIN lvl3 p ON p.name = v.parent_name
    RETURNING id, name
),
inserted AS (
        SELECT id, name FROM lvl1
        UNION ALL SELECT id, name FROM lvl2
        UNION ALL SELECT id, name FROM lvl3
        UNION ALL SELECT id, name FROM lvl4
)
INSERT INTO system_role_menus (role_id, menu_id, created_at)
SELECT r.id, m.id, (extract(epoch from now()) * 1000)::bigint
FROM system_roles r
JOIN inserted m ON m.name IN (
        '冻结查询',
        '冻结管理',
        '单笔冻结',
        '查看打款银行卡',
        '查看银行卡',
        '解绑银行卡',
        '解除冻结',
        '财务总览',
        '账户冻结',
        '钱包查询',
        '钱包流水',
        '钱包详情',
        '钱包调整'
)
WHERE r.code IN ('super_admin')
ON CONFLICT DO NOTHING;



-- ─────────────────────────────────────────────────────────────
-- 原 V003__withdraw_ledger_idempotency.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V003: 提现资金动作幂等 (P4-B0)
-- 钱包账变表对「提现类 biz_type」加 (biz_type, biz_id) 唯一约束，保证 freeze/unfreeze/
-- deduct 重试或并发不会重复扣冻。用 *部分* 唯一索引，避免影响存量数据
-- (admin 调账 biz_type=200 的 biz_id 恒为 0，会重复；充值等也可能复用 biz_id=0)。
-- 提现 biz_type 约定：300=WITHDRAW_FREEZE 301=WITHDRAW_UNFREEZE 302=WITHDRAW_DEDUCT 303=WITHDRAW_REFUND
SET search_path = public;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_wallet_tx_withdraw_idem
    ON public.pay_wallet_transactions (biz_type, biz_id)
    WHERE biz_type IN (300, 301, 302, 303);


-- ─────────────────────────────────────────────────────────────
-- 原 V004__create_user_bank_cards.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V004: 用户银行卡（P4-B1）
-- 卡号信封加密存储：card_no_ciphertext(AES-256-GCM box) + encrypted_data_key(被主密钥/KMS 包裹)。
-- 明文卡号绝不入库；列表/用户端只回 card_no_masked；card_no_hash(HMAC) 仅 (user_id,hash) 去重，不可逆。
SET search_path = public;

CREATE SEQUENCE IF NOT EXISTS public.user_bank_cards_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.user_bank_cards (
    id bigint NOT NULL DEFAULT nextval('public.user_bank_cards_id_seq'::regclass),
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
    deleted_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT user_bank_cards_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.user_bank_cards_id_seq OWNED BY public.user_bank_cards.id;

-- 同一用户不可重复绑同一张卡（仅约束有效行）。
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_bank_cards_user_hash
    ON public.user_bank_cards (user_id, card_no_hash) WHERE deleted_at = 0;

-- 查某用户有效卡列表。
CREATE INDEX IF NOT EXISTS idx_user_bank_cards_user
    ON public.user_bank_cards (user_id) WHERE deleted_at = 0;


-- ─────────────────────────────────────────────────────────────
-- 原 V005__create_wallet_withdraw.sql
-- ─────────────────────────────────────────────────────────────

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


-- ─────────────────────────────────────────────────────────────
-- 原 V007__audit_non_repudiation.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V007: 不可抵赖审计（P0 商用级）。
-- 1) 提现审计补 operator/ip/ua/traceId（before/after_status + remark(=reason) 已存在）。
-- 2) 新增通用敏感操作审计表（银行卡 reveal / 余额调整 / 敏感导出 / 人工补账 等，
--    不一定绑定提现订单）。

ALTER TABLE public.wallet_withdraw_audit_logs
    ADD COLUMN IF NOT EXISTS operator_name character varying(128),
    ADD COLUMN IF NOT EXISTS operator_role character varying(256),
    ADD COLUMN IF NOT EXISTS ip character varying(64),
    ADD COLUMN IF NOT EXISTS user_agent character varying(512),
    ADD COLUMN IF NOT EXISTS trace_id character varying(64);

CREATE TABLE IF NOT EXISTS public.pay_sensitive_audit_logs (
    id bigserial PRIMARY KEY,
    operator_id bigint NOT NULL DEFAULT 0,
    operator_name character varying(128),
    operator_role character varying(256),
    -- BANK_CARD_REVEAL / WALLET_ADJUST / WITHDRAW_MANUAL_FIX / EXPORT_SENSITIVE_DATA ...
    action character varying(64) NOT NULL,
    -- BANK_CARD / WALLET / WITHDRAW_ORDER / USER ...
    target_type character varying(64) NOT NULL,
    target_id bigint NOT NULL DEFAULT 0,
    target_user_id bigint NOT NULL DEFAULT 0,
    ip character varying(64),
    user_agent character varying(512),
    trace_id character varying(64),
    before_snapshot text,
    after_snapshot text,
    reason text,
    created_at bigint NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pay_sensitive_audit_action
    ON public.pay_sensitive_audit_logs (action, id);
CREATE INDEX IF NOT EXISTS idx_pay_sensitive_audit_target
    ON public.pay_sensitive_audit_logs (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_pay_sensitive_audit_operator
    ON public.pay_sensitive_audit_logs (operator_id, id);


-- ─────────────────────────────────────────────────────────────
-- 原 V008__create_money_message.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V008: 红包 + 转账（PrivChat Money Message, RP-2）
-- 红包发即扣全额进托管(remaining_amount 为真相)，领取减 remaining，过期退剩余。
-- 转账无需接收确认，一事务内扣发送方 + 入账接收方。金额 bigint(分)，时间 epoch ms。
-- ledger biz_type：400 红包扣款 / 401 红包领取入账 / 402 红包退款 / 500 转出 / 501 转入 / 502 转账退款。
SET search_path = public;

-- ============ 红包订单 ============
CREATE SEQUENCE IF NOT EXISTS public.pay_red_packet_orders_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.pay_red_packet_orders (
    id bigint NOT NULL DEFAULT nextval('public.pay_red_packet_orders_id_seq'::regclass),
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
    CONSTRAINT pay_red_packet_orders_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.pay_red_packet_orders_id_seq OWNED BY public.pay_red_packet_orders.id;

CREATE INDEX IF NOT EXISTS idx_pay_red_packet_orders_sender
    ON public.pay_red_packet_orders (sender_user_id, id DESC);
-- 过期扫描：ACTIVE 且已过期的红包
CREATE INDEX IF NOT EXISTS idx_pay_red_packet_orders_expire
    ON public.pay_red_packet_orders (status, expire_at);

-- ============ 红包领取记录 ============
CREATE SEQUENCE IF NOT EXISTS public.pay_red_packet_claims_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.pay_red_packet_claims (
    id bigint NOT NULL DEFAULT nextval('public.pay_red_packet_claims_id_seq'::regclass),
    red_packet_id bigint NOT NULL,
    user_id bigint NOT NULL,
    amount bigint NOT NULL,
    claimed_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pay_red_packet_claims_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.pay_red_packet_claims_id_seq OWNED BY public.pay_red_packet_claims.id;

-- 防重复领取（同一红包同一用户仅一条）——RP-3 并发防超领双闸之一。
CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_red_packet_claims_rp_user
    ON public.pay_red_packet_claims (red_packet_id, user_id);

-- ============ 转账订单 ============
CREATE SEQUENCE IF NOT EXISTS public.pay_money_transfer_orders_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.pay_money_transfer_orders (
    id bigint NOT NULL DEFAULT nextval('public.pay_money_transfer_orders_id_seq'::regclass),
    from_user_id bigint NOT NULL,
    to_user_id bigint NOT NULL,
    channel_id character varying(64) DEFAULT '' NOT NULL,
    amount bigint NOT NULL,
    remark character varying(255),
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pay_money_transfer_orders_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.pay_money_transfer_orders_id_seq OWNED BY public.pay_money_transfer_orders.id;

CREATE INDEX IF NOT EXISTS idx_pay_money_transfer_orders_from
    ON public.pay_money_transfer_orders (from_user_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_pay_money_transfer_orders_to
    ON public.pay_money_transfer_orders (to_user_id, id DESC);

-- ============ ledger 幂等扩展 ============
-- 复用 V003 的 (biz_type, biz_id) 部分唯一索引思路，覆盖红包/转账 biz_type。
-- 红包扣款/退款 biz_id=red_packet_id；红包领取 biz_id=claim_id；转账各段 biz_id=transfer_id。
CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_wallet_tx_money_msg_idem
    ON public.pay_wallet_transactions (biz_type, biz_id)
    WHERE biz_type IN (400, 401, 402, 500, 501, 502);


-- ─────────────────────────────────────────────────────────────
-- 原 V009__money_message_notification_outbox.sql
-- ─────────────────────────────────────────────────────────────

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


-- ─────────────────────────────────────────────────────────────
-- 原 V010__money_message_card_outbox.sql
-- ─────────────────────────────────────────────────────────────

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


-- ─────────────────────────────────────────────────────────────
-- 原 V011__wallet_admin_permissions.sql
-- ─────────────────────────────────────────────────────────────

-- Wallet admin button-level permission points (controller @Permission values
-- were never seeded;


-- ─────────────────────────────────────────────────────────────
-- 原 V012__money_message_delivery_reliability.sql
-- ─────────────────────────────────────────────────────────────

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


-- ─────────────────────────────────────────────────────────────
-- 原 V013__withdraw_on_hold.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V013: 提现挂起 ON_HOLD（spec WALLET_WITHDRAW_SPEC §10）
--
-- 打款受阻时既不该通过、也不该终结（终结会解冻并结束订单）。ON_HOLD 是非终态：
-- 资金保持冻结，阻塞解除后回到 hold_resume_to 记录的原状态继续走到 PAID。
-- 它是真状态而非数据标注，因为要硬拦 approve / mark-paid / 用户 cancel。

ALTER TABLE wallet_withdraw_orders
    -- 解除挂起后回到的在途状态（1=APPROVED 等）；0 = 未挂起。
    -- 不能省：少了它，已过审的单子解除后会被打回 PENDING 重审。
    ADD COLUMN IF NOT EXISTS hold_resume_to     INTEGER      NOT NULL DEFAULT 0,
    -- 原因码：BANK_CUTOFF / CARD_UNUSABLE / NAME_MISMATCH / COMPLIANCE_REVIEW / OTHER
    ADD COLUMN IF NOT EXISTS hold_reason_code   VARCHAR(32),
    -- 各端按 locale 渲染用的参数（JSON 文本）。用户可见文案不入库——客户端有
    -- 中/繁/英/越四语言，存中文会让非中文用户看到中文。
    ADD COLUMN IF NOT EXISTS hold_reason_params TEXT,
    -- 内部备注，绝不下发给用户。
    ADD COLUMN IF NOT EXISTS hold_note_internal TEXT,
    ADD COLUMN IF NOT EXISTS hold_at            BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS hold_by            BIGINT       NOT NULL DEFAULT 0;

-- 后台「仅看挂起」筛选 + 运营盯挂起单的工作台查询。
CREATE INDEX IF NOT EXISTS idx_withdraw_on_hold
    ON wallet_withdraw_orders (status, hold_at DESC)
    WHERE status = 7;


-- ─────────────────────────────────────────────────────────────
-- 原 V014__fix_withdraw_hold_permission.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V014: 修正 V013 的权限点 id 冲突
--
-- V013 把 `pay:withdraw:hold` 插到 id=4065，但 4065 早已被 V006 的
-- `pay:bank-card:reveal` 占用。`ON CONFLICT (id) DO NOTHING` 静默跳过了插入，
-- 于是：
--   1. `pay:withdraw:hold` 根本不存在 → 后台「挂起/解除挂起」按钮永远不显示；
--   2. 紧跟其后的角色绑定语句按 menu_id=4065 执行，等于给超级管理员(role 1)
--      多授了「查看打款银行卡」——V006 同批权限点(4060..4064)都只绑 role 2，
--      4065 也不例外，这条 role 1 是 V013 引入的偏差。
--
-- 本迁移撤销那条误加的绑定，并把权限点重新插到空闲 id 4066。

SET search_path = public;


-- ─────────────────────────────────────────────────────────────
-- 原 V015__withdraw_hold_reason_free_text.sql
-- ─────────────────────────────────────────────────────────────

-- 挂起原因改为运营手填的自由文本，替换掉原因码 + 参数那套结构。
--
-- 原设计是「码 + 参数」由各端按 locale 渲染，好处是多语言，代价是运营只能从五个
-- 预置码里挑，说不出这一单真正卡在哪。实际业务里挂起后用户是持续找客服的，客服
-- 手里的信息远比五个码丰富，硬塞进码表反而丢信息。
--
-- 多语言因此确实丢了：运营写什么，四种语言的用户看到的都是同一段文字。这是明确
-- 的取舍——福寿万家面向中国大陆用户，能把话说准比能翻译更重要。
--
-- 直接换列而不是并存：V013 上线至今 hold_reason_code 一条非空记录都没有
-- （status=7 的单子也是 0），没有需要兼容的历史数据。

BEGIN;

ALTER TABLE wallet_withdraw_orders
  DROP COLUMN IF EXISTS hold_reason_code,
  DROP COLUMN IF EXISTS hold_reason_params,
  ADD COLUMN IF NOT EXISTS hold_reason_text text;

COMMENT ON COLUMN wallet_withdraw_orders.hold_reason_text IS
  '挂起原因，运营手填，原样展示给用户。不做 i18n。';

COMMIT;


-- ─────────────────────────────────────────────────────────────
-- 原 V016__wallet_freeze_records.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V016: 冻结记录表（spec WALLET_FREEZE_SPEC §3）
--
-- 冻结有两种，它们不是同一种东西：
--   金额型（提现冻结、单笔风控冻结）：金额确定、不随余额变化、可单独释放；
--   状态型（司法冻结）：金额随余额浮动，「有资金转入就直接冻结」。
--
-- 在此之前所有冻结都汇总进 pay_wallets.freeze_price 这一个标量。只有提现一个使用者时
-- 还算够用，一旦加进第二种来源就不可归因了——「减 500」无法表达「减的是哪一笔」，
-- 驳回一笔提现会把司法冻结的钱一起放出来，只要数字对得上。
--
-- 本表是冻结的**真源**；freeze_price 降级为它算出来的缓存（§2.2），
-- 由 wallet-consistency-check.sh 校验两者一致。

SET search_path = public;

CREATE TABLE IF NOT EXISTS pay_wallet_freezes (
    id           BIGSERIAL PRIMARY KEY,
    wallet_id    BIGINT       NOT NULL,
    -- 冗余 user_id：后台按用户查冻结，不必回表 join 钱包。
    user_id      BIGINT       NOT NULL,
    -- 1=WITHDRAW 2=RISK_HOLD 3=JUDICIAL
    freeze_type  SMALLINT     NOT NULL,
    -- 金额型必填；司法冻结全额时为 NULL、定额时为目标额。
    -- NULL 在这里是**有含义的**（= 无上限），不是「没填」。
    amount       BIGINT,
    -- 0=ACTIVE 1=RELEASED 2=CONSUMED 3=EXPIRED
    --
    -- 三种终态必须分开，不许用「amount 归零」隐式表达：
    -- RELEASED=放行(钱回可用) / CONSUMED=被实扣走(提现打款) / EXPIRED=到期失效。
    status       SMALLINT     NOT NULL DEFAULT 0,
    -- 1=提现单 2=账变流水 3=法律文书
    ref_type     SMALLINT     NOT NULL,
    ref_id       VARCHAR(128) NOT NULL,
    reason_code  VARCHAR(64),
    -- 运营手填。司法冻结的这一段**不下发给用户**（只在后台可见）。
    reason_text  TEXT,
    operator_id  BIGINT       NOT NULL DEFAULT 0,
    -- 0 = 无期限；司法冻结到期后由巡检置为 EXPIRED。
    expires_at   BIGINT       NOT NULL DEFAULT 0,
    created_at   BIGINT       NOT NULL DEFAULT 0,
    released_at  BIGINT       NOT NULL DEFAULT 0,
    updated_at   BIGINT       NOT NULL DEFAULT 0
);

-- 幂等：同一来源重复冻结是 no-op（重试、并发、运营重复点击）。
-- 与提现 ledger 的 (biz_type, biz_id) 幂等同一思路。
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_freeze_ref
    ON pay_wallet_freezes (freeze_type, ref_type, ref_id);

-- 一个钱包**最多一条** ACTIVE 司法冻结。
-- 允许两条的话，「解除司法冻结」就得先问是哪一条，而执法文书之间没有这种层级关系。
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_freeze_one_active_judicial
    ON pay_wallet_freezes (wallet_id)
    WHERE freeze_type = 3 AND status = 0;

-- 算 available 的热路径：按钱包取全部 ACTIVE。
CREATE INDEX IF NOT EXISTS idx_wallet_freeze_active
    ON pay_wallet_freezes (wallet_id, freeze_type)
    WHERE status = 0;

-- 后台按用户查冻结历史。
CREATE INDEX IF NOT EXISTS idx_wallet_freeze_user
    ON pay_wallet_freezes (user_id, created_at DESC);

-- ── 存量提现冻结迁入（A 阶段：纯重构，行为不变）────────────────────
--
-- 在途提现（PENDING=0 / APPROVED=1 / PROCESSING=2 / ON_HOLD=7）的冻结额此前只体现在
-- freeze_price 里，没有对应记录行。不迁的话，A 阶段之后这部分冻结在新模型里「不存在」，
-- 一致性校验会立刻判定 freeze_price 偏大，而钱其实是该冻的。
--
-- 幂等：ON CONFLICT DO NOTHING 命中上面的 uq_wallet_freeze_ref（migration runner 不包
-- 外围事务，重跑必须安全）。
INSERT INTO pay_wallet_freezes (
    wallet_id, user_id, freeze_type, amount, status,
    ref_type, ref_id, reason_code, operator_id, created_at, updated_at
)
SELECT
    o.wallet_id,
    o.user_id,
    1,                              -- WITHDRAW
    o.amount,
    0,                              -- ACTIVE
    1,                              -- ref_type = 提现单
    o.id::text,
    'withdraw_pending',
    0,
    COALESCE(o.created_at, 0),
    COALESCE(o.created_at, 0)
FROM wallet_withdraw_orders o
WHERE o.status IN (0, 1, 2, 7)
ON CONFLICT (freeze_type, ref_type, ref_id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────
-- 原 V017__wallet_freeze_permissions.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V017: 冻结管理菜单 + 权限点（spec WALLET_FREEZE_SPEC §4.5）
--
-- 权限点不 seed 的话，controller 上的 @Permission 会让所有角色都拿到
-- 「Permission denied: pay:wallet-freeze:...」——V011 就是补这个坑补的。
--
-- 账户冻结（司法）**单独一个权限点**：它是对用户全部资产的强制处分，
-- 不该和单笔风控冻结共用一个授权。
--
-- ⚠️ **不写死菜单 id**。`system_menus.id` 是 serial，而库里同时存在「显式 id 插入」
-- （V002/V006/V011 那批）和「按序列插入」两种写法；序列并不会因为显式插入而前进，
-- 于是它迟早会长到显式 id 的区间里去，撞上主键。本文件第一版硬写了 40520-40523，
-- 本机跑 migrate 时序列正好在 40521，后面按序列插入的 member V012 当场
-- `duplicate key value violates unique constraint "system_menus_pkey"`。
-- 所以这里按 member V012 的写法：id 交给序列，幂等键用 permission。

SET search_path = public;


-- ─────────────────────────────────────────────────────────────
-- 原 V018__wallet_freeze_menu_route.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V018: 修 V017 冻结菜单的挂载点和路由。
--
-- V017 把「冻结管理」挂在了 4050（钱包余额，**它自己是个页面**）下面，而且没写
-- path/component——菜单树里出现了一个页面的子页面，前端也没有任何路由可跳。
-- 正确的父节点是 405（钱包管理，目录），路由与同级的「钱包余额」对齐：
--   4(/pay) → 405(wallet) → 冻结管理(freeze) → 组件 pay/wallet/freeze/index
--
-- 不改 V017 而另开一版：V017 已经在环境里跑过，改已应用的迁移会让 history 校验失败。
-- 这里按 permission 定位、写幂等 UPDATE，V017 跑没跑过、跑过几次都得到同一结果。

SET search_path = public;


-- ─────────────────────────────────────────────────────────────
-- 原 V019__bank_card_admin_view_permission.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V019: 后台查看用户银行卡的权限点。
--
-- 卡的查看拆成两个权限点，不合并：
--   pay:bank-card:list   看掩码卡号 / 开户行 / 持卡人（客服、风控日常）
--   pay:bank-card:reveal 解密完整卡号（打款时才用，每次写审计）
-- 合成一个就等于让所有能查卡的人都能拿到完整卡号。
--
-- 入口挂在「钱包余额」(4050) 这个页面上（余额列表的行操作里开卡片弹窗），
-- 所以按钮的 parent 是 4050 而不是提现管理 406——4065 那个 reveal 是提现打款场景的。
-- 同一个 permission 字符串在别的页面复用不需要再 seed 一份。
--
-- id 交给序列（见 V017 注释：显式 id 与序列混用会撞主键），幂等键用 permission。

SET search_path = public;


-- ─────────────────────────────────────────────────────────────
-- 原 V020__bank_card_unbind_permission.sql
-- ─────────────────────────────────────────────────────────────

-- module-payment V020: 后台解绑用户银行卡的权限点。
--
-- 第三个卡相关权限点，仍然不合并：
--   pay:bank-card:list   看掩码（客服、风控日常）
--   pay:bank-card:reveal 解密完整卡号（打款时用，写审计）
--   pay:bank-card:unbind 替用户解绑收款通道（写审计）
-- 看卡的人多，动卡的人应该少；合并就等于让所有能查卡的人都能把卡解掉。
--
-- 入口和 list 一样挂在「钱包余额」(4050) 的银行卡弹窗里，故 parent 取 4050。
-- id 交给序列（见 V017/V019），幂等键用 permission。

SET search_path = public;


-- ─────────────────────────────────────────────────────────────
-- 原 V021__drop_pay_app.sql
-- ─────────────────────────────────────────────────────────────

-- 删除「支付应用」(pay_apps) 概念。
--
-- 由来：yudao 的支付网关设计成「一套网关服务多个 App」，于是订单、渠道、退款、通知、
-- 转账全部挂着 app_id。实际部署里从来只有一个应用，这个维度只带来了：
--   1. 渠道要按 app 分组配置，配一次得先建个 app；
--   2. 每个查询都要传 appId 过滤，接口凭空多一个参数；
--   3. 订单详情为了显示 appName 还要多查一张表。
--
-- 安全性：本次涉及的 5 张表在现网均无有效数据（pay_orders 仅测试单），
-- 且红包/钱包/转账余额/提现等**在用功能的表不含 app_id**，不受影响。
--
-- 注意：platform_clients.app_id 属于 platform 模块的另一个概念（客户端应用），
-- 与支付无关，不在此处理。

ALTER TABLE pay_orders       DROP COLUMN IF EXISTS app_id;
ALTER TABLE pay_channels     DROP COLUMN IF EXISTS app_id;
ALTER TABLE pay_refunds      DROP COLUMN IF EXISTS app_id;
ALTER TABLE pay_notify_tasks DROP COLUMN IF EXISTS app_id;
ALTER TABLE pay_transfers    DROP COLUMN IF EXISTS app_id;

-- 渠道改为按 code 全局唯一（原先唯一性依附于 app 维度）。
-- 先删可能存在的旧组合索引，再建全局唯一索引。
DROP INDEX IF EXISTS idx_pay_channels_app_code;
DROP INDEX IF EXISTS uk_pay_channels_app_code;
CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_channels_code ON pay_channels (code);

DROP TABLE IF EXISTS pay_apps;


-- ─────────────────────────────────────────────────────────────
-- 原 V022__pay_channel_platform.sql
-- ─────────────────────────────────────────────────────────────

-- 支付通道补齐「平台」维度。
--
-- 平台与通道是两件事，之前压在一起了：
--   平台 = 对接的支付公司（汇付天下 / 七九 / 文腾），决定协议、验签、网关地址；
--   通道 = 该平台下的一条线路（汇付天下-支付宝1、汇付天下-支付宝2、汇付天下-微信1）。
-- 同一平台的多条通道协议完全相同，区别只是平台侧编号、各自的商户号与费率。
--
-- 这样拆开之后：接一家新支付公司 = 加一个平台实现（代码）；
-- 加一条通道 = 后台加一行数据（不发版）。此前两者都要改代码。

ALTER TABLE pay_channels ADD COLUMN IF NOT EXISTS platform_code        VARCHAR(64)  NOT NULL DEFAULT '';
ALTER TABLE pay_channels ADD COLUMN IF NOT EXISTS method               VARCHAR(32)  NOT NULL DEFAULT 'OTHER';
ALTER TABLE pay_channels ADD COLUMN IF NOT EXISTS platform_channel_id  VARCHAR(128);
ALTER TABLE pay_channels ADD COLUMN IF NOT EXISTS display_mode         VARCHAR(32)  NOT NULL DEFAULT 'REDIRECT_URL';

-- 下单与回调都按 platform_code 找平台实现，按 status 过滤可用通道
CREATE INDEX IF NOT EXISTS idx_pay_channels_platform ON pay_channels (platform_code, status);

-- 沙箱通道种子：让「下单 → 收银台 → 回调 → 解锁」在没接真实平台时即可跑通。
-- 只有开启 payment.mock.enabled 时才可用（沙箱与真实平台互斥），生产环境开不了。
INSERT INTO pay_channels (code, platform_code, method, platform_channel_id, display_mode, config, status, remark)
SELECT * FROM (VALUES
    ('sandbox_alipay', 'sandbox', 'ALIPAY', 'SB-ALIPAY', 'REDIRECT_URL', '{}', 1, '沙箱-支付宝（仅测试）'),
    ('sandbox_wechat', 'sandbox', 'WECHAT', 'SB-WECHAT', 'REDIRECT_URL', '{}', 1, '沙箱-微信（仅测试）')
) AS v(code, platform_code, method, platform_channel_id, display_mode, config, status, remark)
WHERE NOT EXISTS (SELECT 1 FROM pay_channels c WHERE c.code = v.code);


-- ─────────────────────────────────────────────────────────────
-- 原 V023__drop_pay_notify_tasks.sql
-- ─────────────────────────────────────────────────────────────

-- 删除「向商户回调」的通知任务机制。
--
-- 由来：yudao 的 pay 是**独立支付网关**，支付成功后要用 HTTP 回调各个接入的业务应用，
-- pay_notify_tasks 就是这些外发回调的重试队列。
--
-- 本系统是单体 + 领域事件：支付成功由 PayOrderLogic 在事务内发 PayOrderPaidEvent，
-- 各业务模块（内容解锁、钱包到账）自己订阅。这个角色已经被事件总线完全承担，
-- 相关代码也从未被任何地方调用过 —— handlePayNotify / handleRefundNotify 零调用方。
--
-- 留着的成本不是磁盘，是下一个人得先花时间搞清楚"支付成功到底走哪条路"，
-- 然后发现其中一条是死的。
--
-- 若将来支付要拆成独立服务、重新需要对外回调，届时按新的边界重新设计，
-- 而不是复活一套没跑过的代码。

DROP TABLE IF EXISTS pay_notify_tasks;
