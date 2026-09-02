package channel

/**
 * 平台实现注册表：按 platformCode 找到对接实现。
 *
 * 只登记**平台**（一家支付公司一个实现），通道是数据不是代码 ——
 * 运营在后台加一条「汇付天下-支付宝2」是插一行记录，不需要发版。
 */
class PayPlatformRegistry(platforms: List<PayPlatform>) {

    private val byCode: Map<String, PayPlatform> = platforms.associateBy { it.platformCode }

    fun platform(platformCode: String?): PayPlatform? =
        platformCode?.let { byCode[it] }

    fun codes(): Set<String> = byCode.keys

    companion object {
        /**
         * 仅含沙箱平台，用于没接真实平台时跑通链路。
         *
         * 生产环境**不应**使用：沙箱与真实平台互斥，一旦用了它，
         * 真实支付会全部不可用（这正是防止误用的机制）。
         */
        fun sandboxOnly(): PayPlatformRegistry =
            PayPlatformRegistry(listOf(SandboxPayPlatform()))

        /**
         * 默认注册表：沙箱 + 已对接的真实平台。
         *
         * 两者可以同时登记 —— 究竟哪一类可用由 payment.mock.enabled 的互斥决定，
         * 而不是靠这里少注册一个。装配层需要自定义（如注入回调地址）时自行 bind 覆盖。
         */
        fun default(http: neton.http.client.HttpClient): PayPlatformRegistry = PayPlatformRegistry(
            listOf(
                SandboxPayPlatform(),
                // 直连：支付宝
                AlipayPayPlatform(http),
            ) +
                // 同一套四方协议服务多家平台，各挂各的网关与商户号
                SifangPayPlatform.KNOWN_CODES.map { SifangPayPlatform(it, http) }
        )
    }
}
