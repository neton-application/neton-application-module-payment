package controller.admin.refund

import model.PayRefund
import logic.PayRefundLogic
import neton.core.annotations.Controller
import kotlinx.serialization.Serializable
import logic.PayOrderLogic
import neton.core.annotations.Body
import neton.core.annotations.Get
import neton.core.annotations.Post
import neton.core.annotations.PathVariable
import neton.core.annotations.Permission
import neton.core.annotations.Query

@Controller("/pay/refund")
class PayRefundController(
    private val payRefundLogic: PayRefundLogic,
    private val payOrderLogic: PayOrderLogic,
) {

    @Get("/get/{id}")
    @Permission("pay:refund:query")
    suspend fun get(@PathVariable id: Long): PayRefund? {
        return payRefundLogic.get(id)
    }

    @Get("/page")
    @Permission("pay:refund:page")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query channelCode: String? = null,
        @Query merchantRefundId: String? = null,
        @Query status: Int? = null
    ) = payRefundLogic.page(pageNo, pageSize, channelCode, merchantRefundId, status)

    /**
     * 后台发起退款。
     *
     * 返回 null 表示**该平台不支持退款**（四方普遍如此），此时只能线下退款后
     * 用冲正登记 —— 这与"调用失败"是两回事，前者重试没有意义。
     */
    @Post("/create")
    @neton.core.annotations.Permission("pay:refund:create")
    suspend fun create(@Body request: CreateRefundRequest) = payOrderLogic.refund(
        merchantOrderId = request.merchantOrderId,
        merchantRefundId = request.merchantRefundId,
        refundAmount = request.refundAmount,
        reason = request.reason,
    )

}

@Serializable
data class CreateRefundRequest(
    val merchantOrderId: String,
    /** 退款单号，**幂等锚点**：同号重复提交只会退一次。 */
    val merchantRefundId: String,
    /** 退款金额（分），可小于订单金额即部分退款。 */
    val refundAmount: Long,
    val reason: String? = null,
)
