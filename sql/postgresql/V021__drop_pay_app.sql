-- 删除「支付应用」(pay_apps) 概念。
--
-- 由来：yudao 的支付网关设计成「一套网关服务多个 App」，于是订单、渠道、退款、通知、
-- 转账全部挂着 app_id。实际部署里从来只有一个应用，这个维度只带来了：
--   1. 渠道要按 app 分组配置，配一次得先建个 app；
--   2. 每个查询都要传 appId 过滤，接口凭空多一个参数；
--   3. 订单详情为了显示 appName 还要多查一张表。
--
-- 安全性：本次涉及的 5 张表在现网均无有效数据（pay_orders 仅测试单），
-- 且红包/钱包/转账余额/提现等**在用功能的表不含 app_id**，不受影响。
--
-- 注意：platform_clients.app_id 属于 platform 模块的另一个概念（客户端应用），
-- 与支付无关，不在此处理。

ALTER TABLE pay_orders       DROP COLUMN IF EXISTS app_id;
ALTER TABLE pay_channels     DROP COLUMN IF EXISTS app_id;
ALTER TABLE pay_refunds      DROP COLUMN IF EXISTS app_id;
ALTER TABLE pay_notify_tasks DROP COLUMN IF EXISTS app_id;
ALTER TABLE pay_transfers    DROP COLUMN IF EXISTS app_id;

-- 渠道改为按 code 全局唯一（原先唯一性依附于 app 维度）。
-- 先删可能存在的旧组合索引，再建全局唯一索引。
DROP INDEX IF EXISTS idx_pay_channels_app_code;
DROP INDEX IF EXISTS uk_pay_channels_app_code;
CREATE UNIQUE INDEX IF NOT EXISTS uk_pay_channels_code ON pay_channels (code);

DROP TABLE IF EXISTS pay_apps;
