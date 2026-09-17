-- V003：支付渠道补一个能进侧栏的入口，并补齐几处授不出去的写按钮
--
-- ── 支付渠道进不去 ──
-- 「支付渠道」页原先挂在 /pay/app/:id/channels，只能从「应用管理」点进去；而 module-payment
-- 根本没有 /pay/app 控制器（实测 404），于是这一页在侧栏里没有任何入口。
-- 更早一步的问题是它压根建不出渠道：表单第一项是必填的「应用 ID」，而 pay_channels 表没有
-- app_id 列，带上就被严格 body 解码判成 Validation failed。前端已改成扁平页（路由 /pay/channel），
-- 这里给它补上菜单行。
--
-- 对本产品的意义：充值要先有收款通道，没有这一页就只能直接改库。
--
-- ── 写按钮 ──
-- 支付渠道与充值套餐两页的 create / update / delete 都没有菜单行，等于只有 super_admin 能改。
-- 成因与 infra V005 那批相同：baseline 种了页面没种按钮。
INSERT INTO system_menus (name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
SELECT '支付渠道', 'pay:channel:query', 2,
       (SELECT id FROM system_menus WHERE name = '支付中心' AND type = 1 AND parent_id = 0),
       'channel', 'pay/channel/index', 'ant-design:credit-card-outlined', 2, 1,
       (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint
 WHERE EXISTS (SELECT 1 FROM system_menus WHERE name = '支付中心' AND type = 1 AND parent_id = 0)
   AND NOT EXISTS (SELECT 1 FROM system_menus WHERE component = 'pay/channel/index');

INSERT INTO system_menus (name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
SELECT v.name, v.permission, 3,
       (SELECT p.id FROM system_menus p WHERE p.permission = v.parent_permission AND p.type = 2),
       '', NULL, '', v.sort, 1,
       (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint
  FROM (VALUES
    ('渠道新增', 'pay:channel:create', 'pay:channel:query', 1),
    ('渠道修改', 'pay:channel:update', 'pay:channel:query', 2),
    ('渠道删除', 'pay:channel:delete', 'pay:channel:query', 3),
    ('套餐新增', 'pay:wallet-recharge-package:create', 'pay:wallet-recharge-package:query', 1),
    ('套餐修改', 'pay:wallet-recharge-package:update', 'pay:wallet-recharge-package:query', 2),
    ('套餐删除', 'pay:wallet-recharge-package:delete', 'pay:wallet-recharge-package:query', 3)
  ) AS v(name, permission, parent_permission, sort)
 WHERE EXISTS (SELECT 1 FROM system_menus p WHERE p.permission = v.parent_permission AND p.type = 2)
   AND NOT EXISTS (SELECT 1 FROM system_menus m WHERE m.permission = v.permission);
