package logic

import kotlin.test.Test
import kotlin.test.assertEquals

/** WalletBizType 契约（P1 账变明细）：稳定 code + 收支方向，供各端统一渲染。 */
class WalletBizTypeTest {

    @Test
    fun `direction follows price sign`() {
        assertEquals(1, WalletBizType.direction(100), "正 price = 收入")
        assertEquals(-1, WalletBizType.direction(-100), "负 price = 支出")
        assertEquals(0, WalletBizType.direction(0), "0 price = 中性(冻结/解冻)")
    }

    @Test
    fun `code maps known biz types to stable keys`() {
        assertEquals("recharge", WalletBizType.code(WalletBizType.RECHARGE))
        assertEquals("recharge_refund", WalletBizType.code(WalletBizType.RECHARGE_REFUND))
        assertEquals("admin_adjust", WalletBizType.code(WalletBizType.ADMIN_ADJUST))
        assertEquals("withdraw_freeze", WalletBizType.code(WalletBizType.WITHDRAW_FREEZE))
        assertEquals("withdraw_unfreeze", WalletBizType.code(WalletBizType.WITHDRAW_UNFREEZE))
        assertEquals("withdraw_deduct", WalletBizType.code(WalletBizType.WITHDRAW_DEDUCT))
        assertEquals("withdraw_refund", WalletBizType.code(WalletBizType.WITHDRAW_REFUND))
    }

    @Test
    fun `unknown biz type falls back to other`() {
        assertEquals("other", WalletBizType.code(99999))
    }

    @Test
    fun `biz type constants match PayWalletLogic`() {
        // 防止两处常量漂移。
        assertEquals(PayWalletLogic.BIZ_TYPE_RECHARGE, WalletBizType.RECHARGE)
        assertEquals(PayWalletLogic.BIZ_TYPE_WITHDRAW_DEDUCT, WalletBizType.WITHDRAW_DEDUCT)
        assertEquals(PayWalletLogic.BIZ_TYPE_WITHDRAW_REFUND, WalletBizType.WITHDRAW_REFUND)
    }
}
