package logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 入账前的金额核对：平台说「成功」不够，实付必须等于订单金额。
 * 回调与对账补单两条入账路径都走这一条规则。
 */
class PaidAmountCheckTest {

    @Test
    fun exactAmountIsAccepted() {
        assertTrue(PaidAmountCheck.accepts(orderPrice = 1234, paidAmount = 1234, sandbox = false))
    }

    /** 少付（篡改金额后用真签名付了一笔小钱）与多付都不入账：多付同样说明这笔回调对不上这张单。 */
    @Test
    fun differentAmountIsRejected() {
        assertFalse(PaidAmountCheck.accepts(orderPrice = 1234, paidAmount = 1, sandbox = false))
        assertFalse(PaidAmountCheck.accepts(orderPrice = 1234, paidAmount = 1235, sandbox = false))
    }

    /** 真实平台没带回金额 = 无法核对 = 不入账；沙箱没有金额概念，按订单金额处理。 */
    @Test
    fun unknownAmountOnlyPassesInSandbox() {
        assertFalse(PaidAmountCheck.accepts(orderPrice = 1234, paidAmount = null, sandbox = false))
        assertTrue(PaidAmountCheck.accepts(orderPrice = 1234, paidAmount = null, sandbox = true))
        assertFalse(PaidAmountCheck.accepts(orderPrice = 1234, paidAmount = 1, sandbox = true), "沙箱带了金额也要对得上")
    }
}
