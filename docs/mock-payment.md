# 模拟支付（沙箱）联调

在没接真实支付平台时，跑通「下单 → 收银台 → 回调 → 业务解锁」，**包括失败路径**。

## 一次性开启

模拟支付默认关闭，且**必须显式打开**。开关在全局设置里，不在配置文件里：

```bash
# 1) 把各模块的配置定义同步进 system_settings（缺失项按默认值建行）
curl -X POST http://127.0.0.1:8180/admin/system/setting/sync -H "Authorization: Bearer <admin token>"

# 2) 打开模拟支付
curl -X POST http://127.0.0.1:8180/admin/system/setting/update \
  -H "Authorization: Bearer <admin token>" -H "Content-Type: application/json" \
  -d '{"key":"payment.mock.enabled","value":"true"}'
```

也可以直接在后台「系统设置」里找到**模拟支付模式**打开。

> ⚠️ 打开后**全部真实支付平台会被禁用**（沙箱与真实互斥）。这是刻意的：
> 生产环境一旦误开，收款会立刻全断而被立即发现，而不是安静地让人白拿商品。
> 反过来说，**联调真实平台前必须先关掉它**。

## 跑一遍

```bash
# 1) 取可用通道（沙箱开启时只会看到 sandbox_* 通道）
curl http://127.0.0.1:8180/app/pay/channel/list
# → [{"code":"sandbox_alipay","method":"ALIPAY","displayMode":"REDIRECT_URL",...}]

# 2) 下单（以单条内容购买为例），channelCode 必传
curl -X POST "http://127.0.0.1:8180/app/content/purchase/create?itemId=1&channelCode=sandbox_alipay" \
  -H "Authorization: Bearer <user token>"
# → { "orderId":1, "displayMode":"REDIRECT_URL", "payload":"/app/pay/mock/checkout?...", ... }
```

3. 浏览器打开返回的 `payload`，会看到只有两个按钮的收银台：

- **支付成功** → 触发回调 → 订单置为已支付 → 发 `PayOrderPaidEvent` → 业务解锁；
- **支付失败** → 订单**保持待支付**。这是刻意的：真实渠道里一次失败不代表单子作废，
  用户可能换个方式再付，订单应继续等待，直到超时由关单任务处理。

## 验证解锁

```sql
select id, merchant_order_id, status from pay_orders order by id desc limit 5;  -- status: 0待支付 1已支付 2已退款 3已关闭
select * from content_purchase order by id desc limit 5;                        -- 单条内容购买
select * from content_member_vip order by id desc limit 5;                      -- VIP 开通
```

## 常见问题

**收银台 404** —— 模拟支付没开。关闭时相关端点一律 404（不是 403，403 等于告诉对方这里有后门）。

**下单报「不可用的支付通道」** —— 三种可能：通道码不存在、通道 `status != 1`、
或**沙箱开关与通道所属平台不匹配**（沙箱开着却用真实平台的通道，反之亦然）。

**点了成功但没解锁** —— 看 `merchant_order_id` 的业务前缀是否被某个监听者认领
（content 认 `content:item:` 与 `content:vip:`）。前缀不匹配时监听者会跳过，
订单仍会变成已支付。
