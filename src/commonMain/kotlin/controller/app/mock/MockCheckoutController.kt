package controller.app.mock

import channel.PlatformNotifyRequest
import logic.PayOrderLogic
import neton.core.annotations.AllowAnonymous
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Query
import neton.core.http.HttpResponse
import neton.core.http.NotFoundException
import setting.PaymentSettingKeys
import setting.currentValue

/**
 * 模拟收银台：用来在没有真实渠道的情况下跑通「下单 → 支付 → 回调 → 业务解锁」。
 *
 * 页面上只有两个按钮 —— 支付成功、支付失败 —— 点哪个就走哪条回调，
 * 因为失败路径同样需要被测：真实渠道里用户取消、余额不足都会走它，
 * 而只测成功路径的系统，往往是在上线当天才发现失败没处理。
 *
 * **由全局设置 [PaymentSettingKeys.MOCK_PAY_ENABLED] 控制，默认关闭。** 关闭时所有端点
 * 返回 404，对外与「没有这个功能」不可区分 —— 不返回 403，是为了不暴露"这里其实有个后门"。
 */
@AllowAnonymous
@Controller("/pay/mock")
class MockCheckoutController(private val payOrderLogic: PayOrderLogic) {

    /** 收银台页面。真实渠道这一步是跳转到第三方，这里用本地页面替代。 */
    @Get("/checkout")
    suspend fun checkout(
        response: HttpResponse,
        @Query merchantOrderId: String,
        @Query channelCode: String,
    ) {
        requireEnabled()
        response.html(checkoutPage(merchantOrderId, channelCode))
    }

    /** 模拟支付成功：等价于真实渠道回调成功，会触发业务解锁。 */
    @Get("/pay-success")
    suspend fun paySuccess(
        response: HttpResponse,
        @Query merchantOrderId: String,
        @Query channelCode: String,
    ) {
        requireEnabled()
        val ok = payOrderLogic.handleChannelNotify(
            channelCode,
            PlatformNotifyRequest(
                params = mapOf("merchantOrderId" to merchantOrderId, "status" to "success"),
            ),
        )
        response.html(resultPage(ok, "支付成功", merchantOrderId))
    }

    /**
     * 模拟支付失败：订单保持待支付，不触发任何解锁。
     *
     * 刻意不把订单置为失败态：真实渠道里一次失败并不代表这笔单作废，
     * 用户完全可能换个方式再付一次，订单应当继续等待，直到超时由关单任务处理。
     */
    @Get("/pay-fail")
    suspend fun payFail(response: HttpResponse, @Query merchantOrderId: String) {
        requireEnabled()
        response.html(resultPage(false, "支付失败", merchantOrderId))
    }

    private suspend fun requireEnabled() {
        if (!PaymentSettingKeys.MOCK_PAY_ENABLED.currentValue()) throw NotFoundException("Not found")
    }

    private fun checkoutPage(merchantOrderId: String, channelCode: String): String = """
        <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>模拟收银台</title><style>
        body{font-family:system-ui,-apple-system,sans-serif;margin:0;padding:24px;background:#f5f5f7;color:#1d1d1f}
        .card{max-width:420px;margin:40px auto;background:#fff;border-radius:16px;padding:24px;box-shadow:0 2px 12px rgba(0,0,0,.08)}
        .warn{background:#fff4e5;border:1px solid #ffb020;color:#8a5300;padding:12px;border-radius:8px;font-size:13px;margin-bottom:20px}
        .kv{font-size:13px;color:#6e6e73;word-break:break-all;margin-bottom:20px}
        a.btn{display:block;text-align:center;padding:14px;border-radius:10px;text-decoration:none;font-weight:600;margin-bottom:12px}
        .ok{background:#0a84ff;color:#fff}.fail{background:#f2f2f7;color:#1d1d1f}
        </style></head><body><div class="card">
        <div class="warn">⚠️ 模拟收银台，仅用于测试环境。不会产生真实扣款。</div>
        <h2 style="margin:0 0 16px">确认支付</h2>
        <div class="kv">订单号：${merchantOrderId.escapeHtml()}<br>渠道：${channelCode.escapeHtml()}</div>
        <a class="btn ok" href="/app/pay/mock/pay-success?merchantOrderId=${merchantOrderId.urlEncode()}&channelCode=${channelCode.urlEncode()}">支付成功</a>
        <a class="btn fail" href="/app/pay/mock/pay-fail?merchantOrderId=${merchantOrderId.urlEncode()}">支付失败</a>
        </div></body></html>
    """.trimIndent()

    private fun resultPage(ok: Boolean, title: String, merchantOrderId: String): String = """
        <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title><style>
        body{font-family:system-ui,-apple-system,sans-serif;margin:0;padding:24px;background:#f5f5f7;color:#1d1d1f;text-align:center}
        .card{max-width:420px;margin:80px auto;background:#fff;border-radius:16px;padding:32px}
        .icon{font-size:48px}.msg{color:#6e6e73;font-size:13px;margin-top:12px;word-break:break-all}
        </style></head><body><div class="card">
        <div class="icon">${if (ok) "✅" else "❌"}</div>
        <h2>$title</h2>
        <div class="msg">订单号：${merchantOrderId.escapeHtml()}<br>
        ${if (ok) "业务已解锁，可返回应用查看。" else "订单保持待支付，可重新发起。"}</div>
        </div></body></html>
    """.trimIndent()
}

/** 订单号来自外部输入，直出到 HTML 前先转义，避免把测试页变成 XSS 入口。 */
private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

/** 仅处理会破坏 query 结构的字符，测试页不需要完整的百分号编码实现。 */
private fun String.urlEncode(): String =
    replace("%", "%25").replace("&", "%26").replace("=", "%3D")
        .replace("#", "%23").replace(" ", "%20").replace("+", "%2B")
