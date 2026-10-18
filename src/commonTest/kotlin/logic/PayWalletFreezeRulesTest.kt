package logic

import neton.core.http.HttpException
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
        val e = assertFailsWith<HttpException> {
            PayWalletFreezeRules.ensureCanFreeze(balance = 100, freezePrice = 30, amount = 71)
        }
        assertEquals(PaymentErrorCodes.WALLET_AVAILABLE_BALANCE_INSUFFICIENT, e.message)
    }

    @Test
    fun `freeze rejected when amount not positive`() {
        val e = assertFailsWith<HttpException> {
            PayWalletFreezeRules.ensureCanFreeze(balance = 100, freezePrice = 0, amount = 0)
        }
        assertEquals(PaymentErrorCodes.AMOUNT_INVALID, e.message)
        assertEquals(listOf("amount"), e.errors.map { it.path })
        assertEquals(PaymentErrorCodes.AMOUNT_INVALID, e.errors.single().code)
    }

    @Test
    fun `unfreeze rejected when exceeds frozen`() {
        PayWalletFreezeRules.ensureCanUnfreeze(freezePrice = 50, amount = 50)
        val e = assertFailsWith<HttpException> {
            PayWalletFreezeRules.ensureCanUnfreeze(freezePrice = 50, amount = 51)
        }
        assertEquals(PaymentErrorCodes.WALLET_FROZEN_AMOUNT_INSUFFICIENT, e.message)
    }

    @Test
    fun `deduct frozen requires both frozen and balance cover amount`() {
        PayWalletFreezeRules.ensureCanDeductFrozen(balance = 100, freezePrice = 80, amount = 80)
        // frozen short
        assertFailsWith<HttpException> {
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
