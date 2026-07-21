package channel

/**
 * 渠道 client 注册表：按 channelCode 路由到具体渠道实现。
 * 默认注册三个 mock 渠道（qi79 / wechat / alipay）；接真实渠道时在装配层替换或追加。
 */
class PayChannelRegistry(clients: List<PayChannelClient>) {

    private val byCode: Map<String, PayChannelClient> = clients.associateBy { it.channelCode }

    fun client(channelCode: String?): PayChannelClient? =
        channelCode?.let { byCode[it] }

    companion object {
        /** 默认全 mock 渠道，用于跑通链路。 */
        fun defaultMock(): PayChannelRegistry = PayChannelRegistry(
            listOf(
                MockPayChannelClient("qi79"),
                MockPayChannelClient("wechat"),
                MockPayChannelClient("alipay"),
            )
        )
    }
}
