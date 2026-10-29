package controller.app.channel

import kotlinx.serialization.Serializable
import logic.PayOrderLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get

@Controller("/pay/channel")
class PayChannelController(private val payOrderLogic: PayOrderLogic) {

    /**
     * 可用支付渠道 code 列表。
     *
     * 与 [list] 同一口径：只列此刻真能下单的通道（启用 + 模拟 / 真实互斥，见 [PayOrderLogic.payableChannels]）。
     */
    @Get("/get-enable-code-list")
    suspend fun getEnableCodeList(): List<String> = payOrderLogic.payableChannels().map { it.code }

    /**
     * 可用通道列表（收银台用）。
     *
     * 比裸 code 列表多给 [AppPayChannelVO.method] 与 [AppPayChannelVO.displayMode]：
     * 前者决定图标与分组（同一种支付宝可能有多条通道），后者决定拿到 payload 后
     * 是跳转、渲染二维码还是调起 SDK。少了这两样前端只能按 code 硬猜。
     */
    @Get("/list")
    suspend fun list(): List<AppPayChannelVO> = payOrderLogic.payableChannels().map {
        AppPayChannelVO(
            code = it.code,
            method = it.method,
            displayMode = it.displayMode,
            remark = it.remark,
        )
    }
}

/** 收银台可选的一条通道。刻意不含 config —— 那里面是商户号与密钥。 */
@Serializable
data class AppPayChannelVO(
    val code: String,
    val method: String,
    val displayMode: String,
    val remark: String? = null,
)
