-- 支付通道补齐「平台」维度。
--
-- 平台与通道是两件事，之前压在一起了：
--   平台 = 对接的支付公司（汇付天下 / 七九 / 文腾），决定协议、验签、网关地址；
--   通道 = 该平台下的一条线路（汇付天下-支付宝1、汇付天下-支付宝2、汇付天下-微信1）。
-- 同一平台的多条通道协议完全相同，区别只是平台侧编号、各自的商户号与费率。
--
-- 这样拆开之后：接一家新支付公司 = 加一个平台实现（代码）；
-- 加一条通道 = 后台加一行数据（不发版）。此前两者都要改代码。

ALTER TABLE pay_channels ADD COLUMN IF NOT EXISTS platform_code        VARCHAR(64)  NOT NULL DEFAULT '';
ALTER TABLE pay_channels ADD COLUMN IF NOT EXISTS method               VARCHAR(32)  NOT NULL DEFAULT 'OTHER';
ALTER TABLE pay_channels ADD COLUMN IF NOT EXISTS platform_channel_id  VARCHAR(128);
ALTER TABLE pay_channels ADD COLUMN IF NOT EXISTS display_mode         VARCHAR(32)  NOT NULL DEFAULT 'REDIRECT_URL';

-- 下单与回调都按 platform_code 找平台实现，按 status 过滤可用通道
CREATE INDEX IF NOT EXISTS idx_pay_channels_platform ON pay_channels (platform_code, status);

-- 沙箱通道种子：让「下单 → 收银台 → 回调 → 解锁」在没接真实平台时即可跑通。
-- 只有开启 payment.mock.enabled 时才可用（沙箱与真实平台互斥），生产环境开不了。
INSERT INTO pay_channels (code, platform_code, method, platform_channel_id, display_mode, config, status, remark)
SELECT * FROM (VALUES
    ('sandbox_alipay', 'sandbox', 'ALIPAY', 'SB-ALIPAY', 'REDIRECT_URL', '{}', 1, '沙箱-支付宝（仅测试）'),
    ('sandbox_wechat', 'sandbox', 'WECHAT', 'SB-WECHAT', 'REDIRECT_URL', '{}', 1, '沙箱-微信（仅测试）')
) AS v(code, platform_code, method, platform_channel_id, display_mode, config, status, remark)
WHERE NOT EXISTS (SELECT 1 FROM pay_channels c WHERE c.code = v.code);
