package channel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import model.PayChannel

/**
 * 回调地址拼错**不会有任何编译期或运行期报错** —— 只会在生产上表现为渠道打过来 404，
 * 于是「用户付了钱、订单一直待支付」，而对账任务最多十分钟才补一次状态。
 *
 * 所以这几条看着像在测字符串拼接，实际钉的是「发出去的地址」与「注册的路由」必须同源：
 * 期望路径在这里写成字面量，改常量或改路由挂载都会让这条测试变红，作者必须显式确认。
 * 端到端那半边由 harness 补（真下单 → 取 payload 里的 notify_url → 打回去看是不是活路由）。
 *
 * 后半段测 [PayCallbackUrl]：部署方给的地址要分得清「没配」与「写错」，
 * 两者账面结果一样（都没有回调），处置却完全不同。
 */
class PayNotifyRouteTest {

    private fun channel(code: String) = PayChannel(code = code, config = "{}")

    /** `/app` 来自 routing.conf 里 app 组的挂载点，`/pay/channel-notify` 来自 @Controller。 */
    @Test
    fun pathIsTheRegisteredRouteWithTheAppGroupPrefix() {
        assertEquals("/app/pay/channel-notify/alipay_wap_1", payNotifyPath("alipay_wap_1"))
    }

    /**
     * 基址带尾斜杠很常见（多半是从反向代理配置里抄来的），而双斜杠的路径
     * 有些渠道会原样收下、回调时打到一个不存在的地方 —— 与不带斜杠的写法必须等价。
     */
    @Test
    fun trailingSlashAndPaddingOnBaseAreNormalized() {
        val expected = "https://pay.example.com/app/pay/channel-notify/alipay_wap_1"
        assertEquals(expected, payNotifyUrlOf("https://pay.example.com")(channel("alipay_wap_1")))
        assertEquals(expected, payNotifyUrlOf("https://pay.example.com/")(channel("alipay_wap_1")))
        assertEquals(expected, payNotifyUrlOf("  https://pay.example.com/  ")(channel("alipay_wap_1")))
    }

    /**
     * 没配基址时必须给空串，**不能退化成相对地址**。
     *
     * 空串会被平台跳过（不写 notify_url），于是「没配」的表现是走对账兜底；
     * 而相对地址会被渠道当成非法参数拒绝，结果是连单都下不了 —— 比没有回调严重得多。
     */
    @Test
    fun blankBaseYieldsEmptyNotARelativePath() {
        val relative = payNotifyPath("alipay_wap_1")
        for (base in listOf("", "   ", "/", "//")) {
            assertEquals(
                "",
                payNotifyUrlOf(base)(channel("alipay_wap_1")),
                "基址 '$base' 不该拼出相对地址 '$relative'",
            )
        }
    }

    /**
     * 每条渠道各有各的回调路径。路径尾上这个 channelCode 正是回调进来时
     * [controller.app.notify.PayChannelNotifyController] 用来定位渠道的那一个：
     * 回调是匿名的、body 里没有我们的渠道码，全靠路径带进来。
     */
    @Test
    fun urlCarriesTheChannelCode() {
        assertEquals(
            "https://pay.example.com/app/pay/channel-notify/qijiu_alipay_2",
            payNotifyUrlOf("https://pay.example.com")(channel("qijiu_alipay_2")),
        )
    }

    // ==================== 部署配置的三态 ====================

    /** 没配是合法状态：沙箱部署根本没有公网地址，此时空 value 会让平台跳过该参数。 */
    @Test
    fun unsetIsRecognized() {
        for (raw in listOf<String?>(null, "", "   ", "\t")) {
            val url = PayCallbackUrl.of(raw)
            assertTrue(url.isUnset, "空白或缺失该算「没配」: '$raw'")
            assertEquals("", url.value)
            assertNull(url.problem)
        }
    }

    /**
     * 漏 scheme 必须**响**，不能静默当成没配。
     *
     * 两者的账面结果一样（都没有回调），但「运营想配却写错了」必须有人去改：
     * 一个不带 scheme 的地址会被渠道当成非法参数，结果是连单都下不了。
     */
    @Test
    fun missingSchemeIsReportedNotSilentlyDropped() {
        val url = PayCallbackUrl.of("pay.example.com")
        assertFalse(url.isUnset, "写错不等于没配：两者处置不同")
        assertEquals("", url.value, "写错的地址不该被当成能用的发出去")
        val problem = url.problem ?: error("problem 必须非空")
        assertTrue(problem.contains("pay.example.com"), "原因里要带上原始串，否则日志指不到是哪一项: $problem")
        assertTrue(problem.contains("https://"), "原因里要说清要什么: $problem")
    }

    @Test
    fun absoluteUrlsAreKeptVerbatim() {
        assertEquals("https://pay.example.com", PayCallbackUrl.of("https://pay.example.com").value)
        assertEquals("http://127.0.0.1:7080", PayCallbackUrl.of("  http://127.0.0.1:7080  ").value)
        // return_url 的尾斜杠属于页面路径，不该在这里被吃掉（基址那一侧由 payNotifyUrlOf 归一化）
        assertEquals(
            "https://console.example.com/billing/",
            PayCallbackUrl.of("https://console.example.com/billing/").value,
        )
        assertNull(PayCallbackUrl.of("HTTPS://pay.example.com").problem, "scheme 大小写不敏感")
    }

    /**
     * 两层拼在一起才是装配层真正的行为：写错的基址 → 空 value → 空的 notifyUrlOf。
     *
     * 这条把「三态」与「拼地址」接起来，免得两边各自正确、合起来却把一个
     * 非法地址发给了渠道。
     */
    @Test
    fun invalidBaseComposesToNoNotifyUrl() {
        val urlOf = payNotifyUrlOf(PayCallbackUrl.of("pay.example.com").value)
        assertEquals("", urlOf(channel("alipay_wap_1")))
    }
}
