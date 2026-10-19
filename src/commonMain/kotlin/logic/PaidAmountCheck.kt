package logic

/**
 * 入账前的金额核对：平台说「支付成功」还不够，实付必须**恰好**等于订单金额。
 *
 * 回调与对账补单两条入账路径都走这里。多付也拒：金额对不上说明这笔结果对应的不是这张单
 * （或被篡改过），该人工看，而不是按订单金额入账、把差额糊过去。
 * 真实平台没带回金额 = 无法核对 = 不入账；沙箱没有金额概念，没带时按订单金额处理。
 */
object PaidAmountCheck {
    fun accepts(orderPrice: Long, paidAmount: Long?, sandbox: Boolean): Boolean = when (paidAmount) {
        null -> sandbox
        else -> paidAmount == orderPrice
    }
}
