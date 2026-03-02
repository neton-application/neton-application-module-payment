-- =============================================
-- module-payment 初始化数据 (MySQL)
-- =============================================

-- =============================================
-- 菜单数据
-- type: 1=目录, 2=菜单, 3=按钮
-- status: 0=正常, 1=停用
-- =============================================

-- 支付中心（一级目录）
INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4, '支付中心', '', 1, 0, '/pay', NULL, 'ant-design:pay-circle-outlined', 4, 0, 0, 0);

-- =====================
-- 支付中心 (parent_id=4) - 二级菜单
-- =====================
INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (400, '应用管理', 'pay:app:list', 2, 4, 'app', 'pay/app/index', 'ant-design:appstore-outlined', 1, 0, 0, 0);

INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (401, '支付订单', 'pay:order:list', 2, 4, 'order', 'pay/order/index', 'ant-design:account-book-outlined', 2, 0, 0, 0);

INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (402, '退款订单', 'pay:refund:list', 2, 4, 'refund', 'pay/refund/index', 'ant-design:transaction-outlined', 3, 0, 0, 0);

INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (403, '回调通知', 'pay:notify:list', 2, 4, 'notify', 'pay/notify/index', 'ant-design:notification-outlined', 4, 0, 0, 0);

INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (404, '转账订单', 'pay:transfer:list', 2, 4, 'transfer', 'pay/transfer/index', 'ant-design:swap-outlined', 5, 0, 0, 0);

-- 钱包管理（三级目录）
INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (405, '钱包管理', '', 1, 4, 'wallet', NULL, 'ant-design:wallet-outlined', 6, 0, 0, 0);

INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4050, '钱包余额', 'pay:wallet:list', 2, 405, 'balance', 'pay/wallet/balance/index', '', 1, 0, 0, 0);

INSERT IGNORE INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4051, '充值套餐', 'pay:wallet-recharge-package:list', 2, 405, 'recharge-package', 'pay/wallet/rechargePackage/index', '', 2, 0, 0, 0);
