package logic


import controller.app.wallet.toVO
import model.PayWalletFreeze
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 用户端投影：哪些字段能下发，由**服务端**说了算，不靠各端自觉。
 *
 * 这里锁的是一条法律要求而不是产品偏好：账户冻结（司法）的原因是办案依据，
 * 多数司法辖区禁止 tipping-off。风控冻结正相反——钱动不了却不给解释，用户只会
 * 以为系统出错或者钱被吞了。两者共用同一个 `reasonText` 列，所以必须有测试盯着
 * 这个分叉，否则哪天有人「统一一下」就把办案依据发到当事人手机上了。
 */
class WalletFreezeUserProjectionTest {

    private fun freeze(type: Int, reason: String?) = WalletFreezeLogic.VisibleFreeze(
        freeze = PayWalletFreeze(
            id = 1,
            walletId = 1,
            userId = 990000777,
            freezeType = type,
            amount = 10000,
            refType = 2,
            refId = "REF-1",
            reasonText = reason,
            operatorId = 1,
        ),
        shownAmount = 10000,
    )

    @Test
    fun risk_hold_reason_reaches_the_user() {
        val vo = freeze(WalletFreezeType.RISK_HOLD, "资金审核中，预计 3 个工作日内处理完毕").toVO()
        assertEquals("资金审核中，预计 3 个工作日内处理完毕", vo.reasonText)
        assertEquals("REF-1", vo.refId)
    }

    @Test
    fun judicial_reason_never_reaches_the_user() {
        val vo = freeze(WalletFreezeType.JUDICIAL, "配合某案调查，内部依据").toVO()
        assertNull(vo.reasonText, "账户冻结的原因是办案依据，不能下发给当事人")
        assertNull(vo.refId, "法律文书号同理")
    }

    @Test
    fun withdraw_freeze_reason_reaches_the_user() {
        // 提现冻结的原因也是给用户看的（「提现处理中」这类），只有司法是例外。
        val vo = freeze(WalletFreezeType.WITHDRAW, "提现处理中").toVO()
        assertEquals("提现处理中", vo.reasonText)
    }
}
