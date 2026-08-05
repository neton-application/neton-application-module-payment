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

INSERT INTO system_menus (parent_id, name, permission, type, sort, status, created_at, updated_at)
SELECT 4050, '查看银行卡', 'pay:bank-card:list', 3, 7, 1,
       (extract(epoch from now()) * 1000)::bigint,
       (extract(epoch from now()) * 1000)::bigint
WHERE NOT EXISTS (
    SELECT 1 FROM system_menus WHERE permission = 'pay:bank-card:list'
);

-- 绑定给内置管理员。reveal 一并兜底：V006 只 seed 了菜单行，没建角色绑定，
-- 于是「查看打款银行卡」这个权限点在新环境里谁都没有。
INSERT INTO system_role_menus (role_id, menu_id)
SELECT role.id, menu.id
FROM system_roles role
CROSS JOIN system_menus menu
WHERE role.code IN ('super_admin', 'admin')
  AND menu.permission IN ('pay:bank-card:list', 'pay:bank-card:reveal')
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menus existing
       WHERE existing.role_id = role.id AND existing.menu_id = menu.id
  );

SELECT setval(
    pg_get_serial_sequence('system_menus', 'id'),
    GREATEST((SELECT COALESCE(max(id), 1) FROM system_menus), 1)
);
