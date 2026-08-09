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

INSERT INTO system_menus (parent_id, name, permission, type, sort, status, created_at, updated_at)
SELECT 4050, '解绑银行卡', 'pay:bank-card:unbind', 3, 8, 1,
       (extract(epoch from now()) * 1000)::bigint,
       (extract(epoch from now()) * 1000)::bigint
WHERE NOT EXISTS (
    SELECT 1 FROM system_menus WHERE permission = 'pay:bank-card:unbind'
);

INSERT INTO system_role_menus (role_id, menu_id)
SELECT role.id, menu.id
FROM system_roles role
CROSS JOIN system_menus menu
WHERE role.code IN ('super_admin', 'admin')
  AND menu.permission = 'pay:bank-card:unbind'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menus existing
       WHERE existing.role_id = role.id AND existing.menu_id = menu.id
  );

SELECT setval(
    pg_get_serial_sequence('system_menus', 'id'),
    GREATEST((SELECT COALESCE(max(id), 1) FROM system_menus), 1)
);
