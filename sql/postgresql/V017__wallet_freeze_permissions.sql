-- module-payment V017: 冻结管理菜单 + 权限点（spec WALLET_FREEZE_SPEC §4.5）
--
-- 权限点不 seed 的话，controller 上的 @Permission 会让所有角色都拿到
-- 「Permission denied: pay:wallet-freeze:...」——V011 就是补这个坑补的。
--
-- 账户冻结（司法）**单独一个权限点**：它是对用户全部资产的强制处分，
-- 不该和单笔风控冻结共用一个授权。
--
-- ⚠️ **不写死菜单 id**。`system_menus.id` 是 serial，而库里同时存在「显式 id 插入」
-- （V002/V006/V011 那批）和「按序列插入」两种写法；序列并不会因为显式插入而前进，
-- 于是它迟早会长到显式 id 的区间里去，撞上主键。本文件第一版硬写了 40520-40523，
-- 本机跑 migrate 时序列正好在 40521，后面按序列插入的 member V012 当场
-- `duplicate key value violates unique constraint "system_menus_pkey"`。
-- 所以这里按 member V012 的写法：id 交给序列，幂等键用 permission。

SET search_path = public;

-- 父菜单：冻结管理（挂在 4050 钱包管理下）。
INSERT INTO system_menus (parent_id, name, permission, type, sort, status, created_at, updated_at)
SELECT 4050, '冻结管理', 'pay:wallet-freeze:menu', 2, 6, 1,
       (extract(epoch from now()) * 1000)::bigint,
       (extract(epoch from now()) * 1000)::bigint
WHERE NOT EXISTS (
    SELECT 1 FROM system_menus WHERE permission = 'pay:wallet-freeze:menu'
);

-- 按钮级权限点。parent_id 从父菜单查，同样不写死。
WITH required(name, permission, sort) AS (
    VALUES
        ('冻结查询', 'pay:wallet-freeze:list',     1),
        ('单笔冻结', 'pay:wallet-freeze:place',    2),
        ('账户冻结', 'pay:wallet-freeze:judicial', 3),
        ('解除冻结', 'pay:wallet-freeze:release',  4)
)
INSERT INTO system_menus (parent_id, name, permission, type, sort, status, created_at, updated_at)
SELECT parent.id, required.name, required.permission, 3, required.sort, 1,
       (extract(epoch from now()) * 1000)::bigint,
       (extract(epoch from now()) * 1000)::bigint
FROM required
CROSS JOIN (SELECT id FROM system_menus WHERE permission = 'pay:wallet-freeze:menu' LIMIT 1) parent
WHERE NOT EXISTS (
    SELECT 1 FROM system_menus existing WHERE existing.permission = required.permission
);

-- 绑定给内置管理员。按 role code 匹配，不按数字 id（角色 id 各环境不保证一致）。
INSERT INTO system_role_menus (role_id, menu_id)
SELECT role.id, menu.id
FROM system_roles role
CROSS JOIN system_menus menu
WHERE role.code IN ('super_admin', 'admin')
  AND menu.permission IN (
      'pay:wallet-freeze:menu',
      'pay:wallet-freeze:list',
      'pay:wallet-freeze:place',
      'pay:wallet-freeze:judicial',
      'pay:wallet-freeze:release'
  )
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menus existing
       WHERE existing.role_id = role.id AND existing.menu_id = menu.id
  );

-- 把序列推到 max(id) 之上，修掉存量显式 id 留下的隐患。
-- 不做这一步的话，下一个按序列插入菜单的 migration 还会撞上 V002/V006/V011 写死的那些 id。
SELECT setval(
    pg_get_serial_sequence('system_menus', 'id'),
    GREATEST((SELECT COALESCE(max(id), 1) FROM system_menus), 1)
);
