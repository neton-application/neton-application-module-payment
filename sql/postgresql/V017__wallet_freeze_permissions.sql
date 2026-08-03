-- module-payment V017: 冻结管理菜单 + 权限点（spec WALLET_FREEZE_SPEC §4.5）
--
-- 权限点不 seed 的话，controller 上的 @Permission 会让所有角色都拿到
-- 「Permission denied: pay:wallet-freeze:...」——V011 就是补这个坑补的，这里一次做全。
--
-- 账户冻结（司法）**单独一个权限点**：它是对用户全部资产的强制处分，
-- 不该和单笔风控冻结共用一个授权。

SET search_path = public;

INSERT INTO system_menus (id, parent_id, name, permission, type, sort, status, created_at, updated_at) VALUES
 (4052, 4050, '冻结管理', '', 2, 6, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint)
ON CONFLICT (id) DO NOTHING;

INSERT INTO system_menus (id, parent_id, name, permission, type, sort, status, created_at, updated_at) VALUES
 (40520, 4052, '冻结查询', 'pay:wallet-freeze:list',     3, 1, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint),
 (40521, 4052, '单笔冻结', 'pay:wallet-freeze:place',    3, 2, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint),
 (40522, 4052, '账户冻结', 'pay:wallet-freeze:judicial', 3, 3, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint),
 (40523, 4052, '解除冻结', 'pay:wallet-freeze:release',  3, 4, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint)
ON CONFLICT (id) DO NOTHING;

-- 绑定给角色 1/2（与 V011 的钱包权限同口径）。
INSERT INTO system_role_menus (role_id, menu_id)
SELECT r.id, m.id FROM system_roles r, system_menus m
 WHERE r.id IN (1, 2) AND m.id IN (4052, 40520, 40521, 40522, 40523)
   AND NOT EXISTS (SELECT 1 FROM system_role_menus rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
