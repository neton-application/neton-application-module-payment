package channel

import model.PayChannel

/**
 * 渠道回调的路由常量与拼地址纯函数：**注册的路由**与**发出去的 notify_url** 共用同一份字符串。
 *
 * 为什么值得单独立一个文件：这两处一旦漂移，症状是「用户付了钱、订单一直待支付」。
 * 渠道打过来是 404，重试几次就放弃；对账任务虽然会主动查单兜底，但要等十来分钟
 * （十分钟一轮，且刚下单 2 分钟内的单会被跳过），而且它只补得了状态、补不回渠道那边的重试记录。更要紧的是**这种缺陷在本地测不出来**
 * —— 本机没有公网地址，回调永远打不进来，跑通了也证明不了什么。
 *
 * 这里只放**不含主机名**的那一半：`/app` 来自 routing.conf 里 app 组的挂载点，
 * `/pay/channel-notify` 来自 [controller.app.notify.PayChannelNotifyController] 的 `@Controller`
 * —— 后者直接引用 [PAY_NOTIFY_ROUTE]，所以改路由必然连带改 notify_url，反之亦然。
 *
 * 末尾的 [PayCallbackUrl] 管另一半：部署方给的地址要分得清「没配」与「写错」，
 * 两者的处置完全不同（前者留一行日志即可，后者必须响）。
 */

/**
 * app 路由组的挂载前缀，取自 `application/config/routing.conf` 的 `group = "app"` / `mount = "/app"`。
 *
 * 框架按包名 `controller.app.*` 推断路由组并挂前缀（见 routing.conf 的注释）。
 * 这里重复一份是为了让拼出来的回调地址是完整路径：渠道服务器只认绝对 URL，
 * 少这一段就是 404。
 */
const val APP_ROUTE_PREFIX = "/app"

/** 渠道回调控制器的基础路径，被 `@Controller` 与下面的拼地址函数共用。 */
const val PAY_NOTIFY_ROUTE = "/pay/channel-notify"

/** 某条渠道的回调路径，不含主机名。 */
fun payNotifyPath(channelCode: String): String = "$APP_ROUTE_PREFIX$PAY_NOTIFY_ROUTE/$channelCode"

/**
 * 把部署方给出的**后端公网基址**变成平台需要的 `notifyUrlOf`。
 *
 * 基址为空时返回空串。这不是偷懒：[AlipayPayPlatform] / [SifangPayPlatform] 对空值一律
 * **不写** notify_url，于是「没配」的表现与从前一致（走对账兜底），而不是拼出一个
 * `"/app/pay/channel-notify/x"` 这样的相对地址发给渠道 —— 相对地址会被当成非法参数，
 * 结果是**连单都下不了**，比没有回调严重得多。
 *
 * 结尾斜杠会被去掉，`https://pay.example.com/` 与 `https://pay.example.com` 拼出来一样。
 * 这一条看着琐碎，但基址通常是从反向代理配置里抄来的，带斜杠很常见，而双斜杠的 URL
 * 有些渠道会原样收下、回调时打到一个不存在的路径上。
 */
fun payNotifyUrlOf(publicBase: String): (PayChannel) -> String = { channel ->
    val base = publicBase.trim().trimEnd('/')
    if (base.isEmpty()) "" else base + payNotifyPath(channel.code)
}

/**
 * 部署方给的一个公网地址，三态分得开：**没配** / **能用** / **写错了**。
 *
 * 三态必须分开，因为处置完全不同。没配是合法配置（沙箱部署根本没有公网地址），
 * 启动日志里留一行就够；写错了则必须响 —— 漏掉 scheme 的 `pay.example.com` 会被渠道
 * 当成非法参数，表现是**连单都下不了**，而对方只回一句「参数错误」，排查的人不会想到
 * 是自己少写了 `https://`。静默接受它的结果比不接受更糟：订单一条也下不成，
 * 而账面上什么都没有，看上去像「没人来付款」。
 *
 * 只校验 scheme，不校验可达性：可达性得发请求才知道，而启动期不该依赖第三方。
 * 更要紧的是「我们这儿连不连得上」与「渠道服务器连不连得上」本来就不是一件事：
 * 内网跑的进程常常连不到自己的公网入口（发夹弯），而渠道打得进来。
 * 拿前者去否决后者会把一个好配置误判成坑。
 */
class PayCallbackUrl private constructor(
    /**
     * 能用的地址。没配或写错时是空串 —— 空串会让平台**跳过**该参数（见 [payNotifyUrlOf]），
     * 于是两种情况都退化成「没有回调地址」，也就是修复前的行为，不会更糟。
     */
    val value: String,
    /** 写错时的原因，带上原始串好让日志能指名道姓；没配与能用都是 null。 */
    val problem: String?,
) {
    /** 没配（缺失或全空白）。与「写错」严格分开，见类注释。 */
    val isUnset: Boolean get() = value.isEmpty() && problem == null

    companion object {
        /** 环境变量原值 → 三态。null 与空白一律算「没配」。 */
        fun of(raw: String?): PayCallbackUrl {
            val t = raw?.trim().orEmpty()
            if (t.isEmpty()) return PayCallbackUrl("", null)
            val lower = t.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                return PayCallbackUrl(
                    "",
                    "必须以 http:// 或 https:// 开头（渠道只收绝对地址），实际给的是 '$t'",
                )
            }
            return PayCallbackUrl(t, null)
        }
    }
}
