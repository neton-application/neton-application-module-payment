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
