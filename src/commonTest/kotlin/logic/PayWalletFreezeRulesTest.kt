package logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PayWalletFreezeRulesTest {

    @Test
    fun `available is balance minus frozen`() {
        assertEquals(70, PayWalletFreezeRules.available(balance = 100, freezePrice = 30))
        assertEquals(0, PayWalletFreezeRules.available(balance = 100, freezePrice = 100))
    }

    @Test
    fun `freeze allowed when available covers amount`() {
        PayWalletFreezeRules.ensureCanFreeze(balance = 100, freezePrice = 30, amount = 70)
    }

    @Test
    fun `freeze rejected when available insufficient`() {
        assertFailsWith<IllegalArgumentException> {
            PayWalletFreezeRules.ensureCanFreeze(balance = 100, freezePrice = 30, amount = 71)
        }
    }

    @Test
    fun `freeze rejected when amount not positive`() {
        assertFailsWith<IllegalArgumentException> {
            PayWalletFreezeRules.ensureCanFreeze(balance = 100, freezePrice = 0, amount = 0)
        }
    }

    @Test
    fun `unfreeze rejected when exceeds frozen`() {
        PayWalletFreezeRules.ensureCanUnfreeze(freezePrice = 50, amount = 50)
        assertFailsWith<IllegalArgumentException> {
            PayWalletFreezeRules.ensureCanUnfreeze(freezePrice = 50, amount = 51)
        }
    }

    @Test
    fun `deduct frozen requires both frozen and balance cover amount`() {
        PayWalletFreezeRules.ensureCanDeductFrozen(balance = 100, freezePrice = 80, amount = 80)
        // frozen short
        assertFailsWith<IllegalArgumentException> {
            PayWalletFreezeRules.ensureCanDeductFrozen(balance = 100, freezePrice = 79, amount = 80)
        }
    }

    @Test
    fun `ordinary debit must not dip into frozen funds`() {
        // balance 100, frozen 80 → available 20. debit 30 → balanceAfter 70 < frozen 80 → unsafe
        assertFalse(PayWalletFreezeRules.debitKeepsFrozenSafe(balanceAfter = 70, freezePrice = 80))
        // debit 20 → balanceAfter 80 == frozen 80 → safe
        assertTrue(PayWalletFreezeRules.debitKeepsFrozenSafe(balanceAfter = 80, freezePrice = 80))
        // no frozen → always safe
        assertTrue(PayWalletFreezeRules.debitKeepsFrozenSafe(balanceAfter = 0, freezePrice = 0))
    }
}
