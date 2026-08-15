-- 删除「向商户回调」的通知任务机制。
--
-- 由来：yudao 的 pay 是**独立支付网关**，支付成功后要用 HTTP 回调各个接入的业务应用，
-- pay_notify_tasks 就是这些外发回调的重试队列。
--
-- 本系统是单体 + 领域事件：支付成功由 PayOrderLogic 在事务内发 PayOrderPaidEvent，
-- 各业务模块（内容解锁、钱包到账）自己订阅。这个角色已经被事件总线完全承担，
-- 相关代码也从未被任何地方调用过 —— handlePayNotify / handleRefundNotify 零调用方。
--
-- 留着的成本不是磁盘，是下一个人得先花时间搞清楚"支付成功到底走哪条路"，
-- 然后发现其中一条是死的。
--
-- 若将来支付要拆成独立服务、重新需要对外回调，届时按新的边界重新设计，
-- 而不是复活一套没跑过的代码。

DROP TABLE IF EXISTS pay_notify_tasks;

-- 后台菜单同步移除（V002 里插的那行）
DELETE FROM system_role_menus WHERE menu_id = 403;
DELETE FROM system_menus WHERE id = 403;
