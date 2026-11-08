-- 通用消费余额以百万分之一美元记账，支持按量计费和在途预留。
CREATE TABLE pay_consumption_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0,
    reserved_balance BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE pay_consumption_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    ref VARCHAR(128),
    created_at BIGINT,
    operator_id BIGINT,
    remark VARCHAR(500)
);

CREATE UNIQUE INDEX uk_pay_consumption_tx_ref ON pay_consumption_transactions(ref) WHERE ref IS NOT NULL;
CREATE INDEX idx_pay_consumption_tx_user_id ON pay_consumption_transactions(user_id, id DESC);
