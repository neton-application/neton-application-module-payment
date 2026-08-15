package controller.app.channel

import kotlinx.serialization.Serializable
import logic.PayChannelLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get

@Controller("/pay/channel")
class PayChannelController(private val channelLogic: PayChannelLogic) {

    /**
     * 可用支付渠道 code 列表。
     *
     * 委托给 [PayChannelLogic]，不再自己拼查询：原实现的 `where {}` 里两个条件没有用
     * `and()` 包起来，只有最后一条生效；且用的是 `status eq 0`，与 admin 侧的
     * `status eq 1` 相反（yudao 的 0=开启 遗留，neton 约定 1=启用），两边结果对不上。
     */
    @Get("/get-enable-code-list")
    suspend fun getEnableCodeList(): List<String> = channelLogic.getEnableCodeList()

    /**
     * 可用通道列表（收银台用）。
     *
     * 比裸 code 列表多给 [AppPayChannelVO.method] 与 [AppPayChannelVO.displayMode]：
     * 前者决定图标与分组（同一种支付宝可能有多条通道），后者决定拿到 payload 后
     * 是跳转、渲染二维码还是调起 SDK。少了这两样前端只能按 code 硬猜。
     */
    @Get("/list")
    suspend fun list(): List<AppPayChannelVO> = channelLogic.enabledForApp().map {
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
