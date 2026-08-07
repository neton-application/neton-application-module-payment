package logic


import controller.app.wallet.toVO
import model.PayWalletFreeze
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 用户端投影：哪些字段能下发，由**服务端**说了算，不靠各端自觉。
 *
 * 产品决定：三类冻结的说明都下发，**包括账户冻结（司法）**——钱被冻住的人有权知道原因。
 * 代价是运营写「冻结说明」时必须清楚它会出现在当事人手机上，后台文案已明确标注。
 *
 * 这里真正要钉死的是另一条：**服务端自造的幂等键不是单据号**，绝不能当文书号显示。
 * 运营留空单据时服务端会生成 `auto:{user}:{op}:{ms}`，那串东西摆到用户面前
 * 就是一行乱码，看着像系统出错。
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
    fun judicial_explanation_also_reaches_the_user() {
        val vo = freeze(WalletFreezeType.JUDICIAL, "账户被依法冻结，请联系客服了解详情").toVO()
        assertEquals("账户被依法冻结，请联系客服了解详情", vo.reasonText)
        assertEquals("REF-1", vo.refId, "运营填了文书号就显示")
    }

    @Test
    fun a_generated_key_is_never_shown_as_a_document_number() {
        val auto = WalletFreezeLogic.AUTO_REF_PREFIX + "990000777:1:1786085961879"
        val vo = WalletFreezeLogic.VisibleFreeze(
            freeze = PayWalletFreeze(
                id = 1, walletId = 1, userId = 990000777,
                freezeType = WalletFreezeType.RISK_HOLD, amount = 10000,
                refType = 2, refId = auto, reasonText = "资金审核中", operatorId = 1,
            ),
            shownAmount = 10000,
        ).toVO()
        assertNull(vo.refId, "自造幂等键不是单据号，不下发")
        assertEquals("资金审核中", vo.reasonText, "说明照常下发")
    }

    @Test
    fun withdraw_freeze_reason_reaches_the_user() {
        // 提现冻结的原因也是给用户看的（「提现处理中」这类），只有司法是例外。
        val vo = freeze(WalletFreezeType.WITHDRAW, "提现处理中").toVO()
        assertEquals("提现处理中", vo.reasonText)
    }
}
