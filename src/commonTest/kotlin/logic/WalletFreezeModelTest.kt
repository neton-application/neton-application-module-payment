package logic

import logic.WalletFreezeModel.JudicialHold
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec WALLET_FREEZE_SPEC §2.1：available 的唯一公式。 */
class WalletFreezeModelTest {

    // ── 金额型冻结（提现 / 单笔风控）────────────────────────────────

    @Test
    fun amountHoldsReduceAvailableButNotBalance() {
        assertEquals(700, WalletFreezeModel.available(balance = 1000, amountHolds = 300, judicial = null))
        assertEquals(300, WalletFreezeModel.freezePriceCache(1000, 300, null))
    }

    @Test
    fun noFreezeMeansEverythingIsAvailable() {
        assertEquals(1000, WalletFreezeModel.available(1000, 0, null))
    }

    // ── 全额司法冻结 ────────────────────────────────────────────

    /**
     * 全额冻结下可用余额恒为 0——**这就是「有资金转入就直接冻结」**。
     * 不需要在入账上挂任何钩子：余额涨了，冻结额跟着涨。
     */
    @Test
    fun aFullAccountFreezeAbsorbsIncomingMoney() {
        val judicial = JudicialHold(targetAmount = null)
        assertEquals(0, WalletFreezeModel.available(1000, 0, judicial))

        // 用户收了一个 500 的红包：余额涨到 1500，可用仍然是 0。
        assertEquals(0, WalletFreezeModel.available(1500, 0, judicial))
        assertEquals(1500, WalletFreezeModel.freezePriceCache(1500, 0, judicial))
    }

    @Test
    fun aFullAccountFreezeCoexistsWithAPendingWithdrawal() {
        // 提现已冻 300，此时司法冻结到达：剩下的 700 也被冻住，两者不重复计算。
        val judicial = JudicialHold(targetAmount = null)
        assertEquals(700, WalletFreezeModel.judicialHold(1000, amountHolds = 300, judicial))
        assertEquals(1000, WalletFreezeModel.freezePriceCache(1000, 300, judicial))
        assertEquals(0, WalletFreezeModel.available(1000, 300, judicial))
    }

    // ── 定额司法冻结 ────────────────────────────────────────────

    @Test
    fun aCappedFreezeLeavesTheRestSpendable() {
        val judicial = JudicialHold(targetAmount = 5_000_00)
        assertEquals(5_000_00, WalletFreezeModel.judicialHold(8_000_00, 0, judicial))
        assertEquals(3_000_00, WalletFreezeModel.available(8_000_00, 0, judicial))
    }

    /**
     * 法院要求冻 5 万而账上只有 3 万：先冻住这 3 万，**后续到账继续补足**，
     * 而不是因为不够就一分不冻。
     */
    @Test
    fun aCappedFreezeTakesWhatItCanAndTopsUpFromIncomingMoney() {
        val judicial = JudicialHold(targetAmount = 5_000_00)

        assertEquals(3_000_00, WalletFreezeModel.judicialHold(3_000_00, 0, judicial))
        assertEquals(0, WalletFreezeModel.available(3_000_00, 0, judicial))

        // 到账 1 万：继续吸收，仍未补满，可用还是 0。
        assertEquals(4_000_00, WalletFreezeModel.judicialHold(4_000_00, 0, judicial))
        assertEquals(0, WalletFreezeModel.available(4_000_00, 0, judicial))

        // 补满 5 万之后，多出来的才可用。
        assertEquals(5_000_00, WalletFreezeModel.judicialHold(6_000_00, 0, judicial))
        assertEquals(1_000_00, WalletFreezeModel.available(6_000_00, 0, judicial))
    }

    @Test
    fun aCappedFreezeStacksOnTopOfAmountHolds() {
        // 余额 1000，提现冻 300，法院冻 500 → 可用 200。
        val judicial = JudicialHold(targetAmount = 500)
        assertEquals(500, WalletFreezeModel.judicialHold(1000, 300, judicial))
        assertEquals(800, WalletFreezeModel.freezePriceCache(1000, 300, judicial))
        assertEquals(200, WalletFreezeModel.available(1000, 300, judicial))
    }

    @Test
    fun aTargetAmountMustBePositiveWhenPresent() {
        assertFailsWith<IllegalArgumentException> { JudicialHold(targetAmount = 0) }
        assertFailsWith<IllegalArgumentException> { JudicialHold(targetAmount = -1) }
    }

    // ── 边界 ───────────────────────────────────────────────────

    /** 冻结额一旦超过余额（例如提现冻结后余额被其它路径改小），可用余额不得为负。 */
    @Test
    fun availableNeverGoesNegative() {
        assertEquals(0, WalletFreezeModel.available(100, amountHolds = 300, judicial = null))
        assertEquals(0, WalletFreezeModel.judicialHold(100, amountHolds = 300, JudicialHold(null)))
    }

    // ── 单笔风控冻结的准入 ──────────────────────────────────────

    @Test
    fun aRiskHoldNeedsEnoughAvailableBalance() {
        assertTrue(WalletFreezeModel.canPlaceAmountHold(1000, 0, null, 1000))
        assertFalse(WalletFreezeModel.canPlaceAmountHold(1000, 0, null, 1001))
        assertFalse(WalletFreezeModel.canPlaceAmountHold(1000, 300, null, 800))
    }

    /**
     * 已经被全额司法冻结的账户，再下单笔风控冻结必须失败——钱已经冻完了。
     * 静默按可用余额部分冻结会让运营以为又冻住了一笔。
     */
    @Test
    fun aRiskHoldCannotBePlacedOnTopOfAFullAccountFreeze() {
        assertFalse(WalletFreezeModel.canPlaceAmountHold(1000, 0, JudicialHold(null), 1))
    }

    @Test
    fun aRiskHoldAmountMustBePositive() {
        assertFalse(WalletFreezeModel.canPlaceAmountHold(1000, 0, null, 0))
        assertFalse(WalletFreezeModel.canPlaceAmountHold(1000, 0, null, -5))
    }
}
