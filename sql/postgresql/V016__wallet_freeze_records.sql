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
