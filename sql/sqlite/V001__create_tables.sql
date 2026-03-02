-- =============================================
-- module-payment 全部建表语句 (SQLite)
-- =============================================

CREATE TABLE IF NOT EXISTS pay_apps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    status INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_channels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    config TEXT NOT NULL DEFAULT '',
    status INTEGER NOT NULL DEFAULT 0,
    fee_rate INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_channels_app ON pay_channels(app_id);

CREATE TABLE IF NOT EXISTS pay_orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_id INTEGER NOT NULL,
    merchant_order_id TEXT NOT NULL,
    subject TEXT NOT NULL,
    body TEXT,
    price INTEGER NOT NULL DEFAULT 0,
    channel_code TEXT,
    channel_order_no TEXT,
    status INTEGER NOT NULL DEFAULT 0,
    user_ip TEXT,
    expire_time INTEGER,
    success_time INTEGER,
    notify_time INTEGER,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_orders_app ON pay_orders(app_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pay_orders_merchant ON pay_orders(merchant_order_id);

CREATE TABLE IF NOT EXISTS pay_refunds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_id INTEGER NOT NULL,
    order_id INTEGER NOT NULL,
    merchant_refund_id TEXT NOT NULL,
    channel_code TEXT,
    channel_refund_no TEXT,
    pay_price INTEGER NOT NULL DEFAULT 0,
    refund_price INTEGER NOT NULL DEFAULT 0,
    reason TEXT,
    status INTEGER NOT NULL DEFAULT 0,
    success_time INTEGER,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_refunds_order ON pay_refunds(order_id);

CREATE TABLE IF NOT EXISTS pay_notify_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_id INTEGER NOT NULL,
    type INTEGER NOT NULL DEFAULT 0,
    data_id INTEGER NOT NULL,
    status INTEGER NOT NULL DEFAULT 0,
    merchant_url TEXT,
    notify_times INTEGER NOT NULL DEFAULT 0,
    max_notify_times INTEGER NOT NULL DEFAULT 0,
    next_notify_time INTEGER,
    last_execute_time INTEGER,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_wallets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    balance INTEGER NOT NULL DEFAULT 0,
    total_expense INTEGER NOT NULL DEFAULT 0,
    total_recharge INTEGER NOT NULL DEFAULT 0,
    freeze_price INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pay_wallets_user ON pay_wallets(user_id);

CREATE TABLE IF NOT EXISTS pay_wallet_recharges (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wallet_id INTEGER NOT NULL,
    total_price INTEGER NOT NULL DEFAULT 0,
    pay_price INTEGER NOT NULL DEFAULT 0,
    bonus_price INTEGER NOT NULL DEFAULT 0,
    package_id INTEGER,
    pay_status INTEGER NOT NULL DEFAULT 0,
    pay_order_id INTEGER,
    pay_channel_code TEXT,
    refund_status INTEGER NOT NULL DEFAULT 0,
    refund_total_price INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_recharges_wallet ON pay_wallet_recharges(wallet_id);

CREATE TABLE IF NOT EXISTS pay_wallet_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wallet_id INTEGER NOT NULL,
    biz_type INTEGER NOT NULL DEFAULT 0,
    biz_id INTEGER NOT NULL DEFAULT 0,
    title TEXT NOT NULL,
    price INTEGER NOT NULL DEFAULT 0,
    balance INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_pay_wallet_transactions_wallet ON pay_wallet_transactions(wallet_id);

CREATE TABLE IF NOT EXISTS pay_wallet_recharge_packages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    pay_price INTEGER NOT NULL DEFAULT 0,
    bonus_price INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pay_transfers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_id INTEGER NOT NULL,
    channel_code TEXT,
    merchant_transfer_id TEXT NOT NULL,
    type INTEGER NOT NULL DEFAULT 0,
    price INTEGER NOT NULL DEFAULT 0,
    subject TEXT NOT NULL,
    user_name TEXT,
    account_no TEXT,
    status INTEGER NOT NULL DEFAULT 0,
    success_time INTEGER,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);
