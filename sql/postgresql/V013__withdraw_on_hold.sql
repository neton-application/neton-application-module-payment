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

-- 挂起/解除的按钮权限点（parent=406 提现订单页，沿用 V006 的 id 段）。
-- 挂起与解除同权：能挂就能解，不再细分。
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4065, '挂起/解除挂起', 'pay:withdraw:hold', 3, 406, '', '', '', 6, 1,
        (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint)
ON CONFLICT (id) DO NOTHING;

-- 内置管理员默认拥有（与 V011 同款绑定方式）。
INSERT INTO system_role_menus (role_id, menu_id)
SELECT r.id, m.id FROM system_roles r, system_menus m
 WHERE r.id IN (1, 2) AND m.id = 4065
   AND NOT EXISTS (SELECT 1 FROM system_role_menus rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
