-- module-payment V003: 提现资金动作幂等 (P4-B0)
-- 钱包账变表对「提现类 biz_type」加 (biz_type, biz_id) 唯一约束，保证 freeze/unfreeze/
-- deduct 重试或并发不会重复扣冻。用 *部分* 唯一索引，避免影响存量数据
-- (admin 调账 biz_type=200 的 biz_id 恒为 0，会重复；充值等也可能复用 biz_id=0)。
-- 提现 biz_type 约定：300=WITHDRAW_FREEZE 301=WITHDRAW_UNFREEZE 302=WITHDRAW_DEDUCT 303=WITHDRAW_REFUND
SET search_path = public;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_wallet_tx_withdraw_idem
    ON public.pay_wallet_transactions (biz_type, biz_id)
    WHERE biz_type IN (300, 301, 302, 303);
