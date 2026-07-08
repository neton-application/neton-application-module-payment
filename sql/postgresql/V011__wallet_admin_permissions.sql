-- Wallet admin button-level permission points (controller @Permission values
-- were never seeded; the 4050 page menu existed without them, so every role
-- got "Permission denied: pay:wallet:page"). Bind to roles 1/2.
INSERT INTO system_menus (id, parent_id, name, permission, type, sort, status, created_at, updated_at) VALUES
 (40500, 4050, '钱包查询', 'pay:wallet:page', 3, 1, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint),
 (40501, 4050, '钱包详情', 'pay:wallet:query', 3, 2, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint),
 (40502, 4050, '钱包调整', 'pay:wallet:update', 3, 3, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint),
 (40503, 4050, '财务总览', 'pay:wallet:overview', 3, 4, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint),
 (40504, 4050, '钱包流水', 'pay:wallet-transaction:page', 3, 5, 1, (extract(epoch from now())*1000)::bigint, (extract(epoch from now())*1000)::bigint)
ON CONFLICT (id) DO NOTHING;

INSERT INTO system_role_menus (role_id, menu_id)
SELECT r.id, m.id FROM system_roles r, system_menus m
 WHERE r.id IN (1, 2) AND m.id IN (40500, 40501, 40502, 40503, 40504)
   AND NOT EXISTS (SELECT 1 FROM system_role_menus rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
