-- module-payment V006: 提现订单菜单 + 按钮权限点 seed (P4-F)
-- 复用既有「支付中心」(id=4) 作为财务中心；提现订单单页用状态筛选，操作按钮按权限点细分。
-- type: 1=目录 2=菜单 3=按钮。button 行只承载 permission（path/component 留空），便于后续给财务/客服分权。

-- 提现订单页面
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (406, '提现订单', 'pay:withdraw:list', 2, 4, 'withdraw', 'pay/withdraw/index', 'ant-design:bank-outlined', 7, 1, 0, 0)
ON CONFLICT (id) DO NOTHING;

-- 按钮权限点（parent=406）
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4060, '提现详情', 'pay:withdraw:detail', 3, 406, '', '', '', 1, 1, 0, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4061, '审核通过', 'pay:withdraw:approve', 3, 406, '', '', '', 2, 1, 0, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4062, '驳回', 'pay:withdraw:reject', 3, 406, '', '', '', 3, 1, 0, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4063, '标记已打款', 'pay:withdraw:mark-paid', 3, 406, '', '', '', 4, 1, 0, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4064, '标记失败', 'pay:withdraw:mark-failed', 3, 406, '', '', '', 5, 1, 0, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menus (id, name, permission, type, parent_id, path, component, icon, sort, status, created_at, updated_at)
VALUES (4065, '查看打款银行卡', 'pay:bank-card:reveal', 3, 406, '', '', '', 6, 1, 0, 0)
ON CONFLICT (id) DO NOTHING;
