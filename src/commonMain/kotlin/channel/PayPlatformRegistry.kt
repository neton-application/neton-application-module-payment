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
         * [http] 必须是**受控出站客户端**（neton-http `GuardedHttpClientProvider` 建的）：各平台的网关地址
         * （支付宝 serverUrl、四方 apiUrl）来自后台渠道配置，普通客户端会让一条渠道配置把请求打进内网。
         * [init.PaymentRuntimeBootstrap] 就是这样建的；装配层只经 [PayCallbackHooks] 给回调地址。
         *
         * 两者可以同时登记 —— 究竟哪一类可用由 payment.mock.enabled 的互斥决定，
         * 而不是靠这里少注册一个。注册表只由 payment 自己建（宿主 bind 不到）：平台客户端必须是受控的。
         *
         * **两个回调地址 hook 默认给空串，也就是不写 notify_url / return_url。**
         * 部署在哪个域名下是部署事实，模块自己无从知道；装配层应当 bind 一份
         * [PayCallbackHooks]（见 [payNotifyUrlOf]）。留空不会让下单失败，
         * 只是渠道不会主动回调，退化成靠 PayOrderReconcileJob 查单兜底
         * （十分钟一轮），并且用户付完停在渠道页面上回不来。
         *
         * 做成带默认值的参数、而不是让装配层自己 new 一个平台列表：平台清单在这里，
         * 装配层抄一份的话，日后本模块多接一家支付公司，那份副本会静默过期 ——
         * 表现是新平台的渠道一律「不可用的支付通道」。
         */
        fun default(
            http: neton.http.client.HttpClient,
            notifyUrlOf: (model.PayChannel) -> String = { "" },
            returnUrlOf: (model.PayChannel) -> String = { "" },
        ): PayPlatformRegistry = PayPlatformRegistry(
            listOf(
                SandboxPayPlatform(),
                // 直连：支付宝
                AlipayPayPlatform(http, notifyUrlOf, returnUrlOf),
            ) +
                // 同一套四方协议服务多家平台，各挂各的网关与商户号；
                // 只传 notifyUrlOf：SifangPayPlatform 的下单参数里没有跳回地址这个字段
                SifangPayPlatform.KNOWN_CODES.map { SifangPayPlatform(it, http, notifyUrlOf) }
        )
    }
}
