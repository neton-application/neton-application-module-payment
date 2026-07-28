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

-- 1) 撤销 V013 误加的授权。条件写死 (1, 4065)，不碰 V006 原有的 (2, 4065)。
DELETE FROM system_role_menus WHERE role_id = 1 AND menu_id = 4065;

-- 2) 正确的挂起权限点。挂起与解除同权：能挂就能解，不再细分。
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4066, '挂起/解除挂起', 'pay:withdraw:hold', 3, 406, '', '', '', 7, 1,
        (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint)
ON CONFLICT (id) DO NOTHING;

-- 3) 绑定范围与同批提现权限点(4060..4064)一致：只给 role 2（管理员）。
INSERT INTO system_role_menus (role_id, menu_id)
SELECT r.id, m.id FROM system_roles r, system_menus m
 WHERE r.id = 2 AND m.id = 4066
   AND NOT EXISTS (SELECT 1 FROM system_role_menus rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
