-- V004：支付订单「同步支付状态」按钮权限（POST /admin/pay/order/sync/{id}）
--
-- 用户付了钱但回调没到（渠道抖动、回调地址配错）时，运营在订单列表上点一下，向支付平台查单，
-- 平台确认已付且金额相符才置为已支付并入账。没有「手动标记已支付」：无平台凭证改状态等于凭空发钱。
INSERT INTO system_menus (name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
SELECT '订单同步', 'pay:order:sync', 3,
       (SELECT p.id FROM system_menus p WHERE p.permission = 'pay:order:query' AND p.type = 2),
       '', NULL, '', 1, 1,
       (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint
 WHERE EXISTS (SELECT 1 FROM system_menus p WHERE p.permission = 'pay:order:query' AND p.type = 2)
   AND NOT EXISTS (SELECT 1 FROM system_menus m WHERE m.permission = 'pay:order:sync');

-- 财务负责处理掉单，授给财务角色（角色存在时）
INSERT INTO system_role_menus (role_id, menu_id, created_at)
SELECT (SELECT id FROM system_roles WHERE code = 'finance'), m.id, (extract(epoch from now()) * 1000)::bigint
  FROM system_menus m
 WHERE m.permission = 'pay:order:sync'
   AND EXISTS (SELECT 1 FROM system_roles WHERE code = 'finance')
   AND NOT EXISTS (SELECT 1 FROM system_role_menus rm
                    WHERE rm.role_id = (SELECT id FROM system_roles WHERE code = 'finance') AND rm.menu_id = m.id);
