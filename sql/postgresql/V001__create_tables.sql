-- =============================================
-- module-payment 全部建表语句 (PostgreSQL)
-- =============================================

CREATE TABLE IF NOT EXISTS pay_apps (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_channels (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    config TEXT NOT NULL DEFAULT '',
    status SMALLINT NOT NULL DEFAULT 0,
    fee_rate INT NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_channels_app ON pay_channels(app_id);

CREATE TABLE IF NOT EXISTS pay_orders (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL,
    merchant_order_id VARCHAR(128) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT,
    price BIGINT NOT NULL DEFAULT 0,
    channel_code VARCHAR(64),
    channel_order_no VARCHAR(128),
    status SMALLINT NOT NULL DEFAULT 0,
    user_ip VARCHAR(64),
    expire_time BIGINT,
    success_time BIGINT,
    notify_time BIGINT,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_orders_app ON pay_orders(app_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pay_orders_merchant ON pay_orders(merchant_order_id);

CREATE TABLE IF NOT EXISTS pay_refunds (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    merchant_refund_id VARCHAR(128) NOT NULL,
    channel_code VARCHAR(64),
    channel_refund_no VARCHAR(128),
    pay_price BIGINT NOT NULL DEFAULT 0,
    refund_price BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(512),
    status SMALLINT NOT NULL DEFAULT 0,
    success_time BIGINT,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_refunds_order ON pay_refunds(order_id);

CREATE TABLE IF NOT EXISTS pay_notify_tasks (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL,
    type SMALLINT NOT NULL DEFAULT 0,
    data_id BIGINT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    merchant_url VARCHAR(512),
    notify_times INT NOT NULL DEFAULT 0,
    max_notify_times INT NOT NULL DEFAULT 0,
    next_notify_time BIGINT,
    last_execute_time BIGINT,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    total_expense BIGINT NOT NULL DEFAULT 0,
    total_recharge BIGINT NOT NULL DEFAULT 0,
    freeze_price BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pay_wallets_user ON pay_wallets(user_id);

CREATE TABLE IF NOT EXISTS pay_wallet_recharges (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    total_price BIGINT NOT NULL DEFAULT 0,
    pay_price BIGINT NOT NULL DEFAULT 0,
    bonus_price BIGINT NOT NULL DEFAULT 0,
    package_id BIGINT,
    pay_status SMALLINT NOT NULL DEFAULT 0,
    pay_order_id BIGINT,
    pay_channel_code VARCHAR(64),
    refund_status SMALLINT NOT NULL DEFAULT 0,
    refund_total_price BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_recharges_wallet ON pay_wallet_recharges(wallet_id);

CREATE TABLE IF NOT EXISTS pay_wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    biz_type SMALLINT NOT NULL DEFAULT 0,
    biz_id BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(128) NOT NULL,
    price BIGINT NOT NULL DEFAULT 0,
    balance BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_transactions_wallet ON pay_wallet_transactions(wallet_id);

CREATE TABLE IF NOT EXISTS pay_wallet_recharge_packages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    pay_price BIGINT NOT NULL DEFAULT 0,
    bonus_price BIGINT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_transfers (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL,
    channel_code VARCHAR(64),
    merchant_transfer_id VARCHAR(128) NOT NULL,
    type SMALLINT NOT NULL DEFAULT 0,
    price BIGINT NOT NULL DEFAULT 0,
    subject VARCHAR(255) NOT NULL,
    user_name VARCHAR(64),
    account_no VARCHAR(128),
    status SMALLINT NOT NULL DEFAULT 0,
    success_time BIGINT,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);
