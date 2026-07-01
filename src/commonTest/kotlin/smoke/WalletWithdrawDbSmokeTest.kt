package smoke

import kotlinx.coroutines.runBlocking
import logic.PayWalletLogic
import logic.OperatorContext
import logic.UserBankCardLogic
import logic.WalletWithdrawLogic
import logic.WithdrawStateMachine
import logic.crypto.BankCardCrypto
import logic.crypto.EnvWalletCryptoKeyProvider
import model.PayWallet
import model.PaySensitiveAuditLog
import model.PayWalletTransaction
import model.UserBankCard
import model.WalletWithdrawAuditLog
import model.WalletWithdrawOrder
import neton.core.config.getEnv
import neton.core.http.HttpException
import neton.database.adapter.sqlx.SqlxDatabase
import neton.database.config.DatabaseConfig
import neton.database.config.DatabaseDriver
import neton.database.dsl.*
import neton.logging.Fields
import neton.logging.Logger
import table.PaySensitiveAuditLogTable
import table.PayWalletTable
import table.PayWalletTransactionTable
import table.UserBankCardTable
import table.WalletWithdrawAuditLogTable
import table.WalletWithdrawOrderTable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object NoopLogger : Logger {
    override fun trace(msg: String, fields: Fields) {}
    override fun debug(msg: String, fields: Fields) {}
    override fun info(msg: String, fields: Fields) {}
    override fun warn(msg: String, fields: Fields, cause: Throwable?) {}
    override fun error(msg: String, fields: Fields, cause: Throwable?) {}
}

/**
 * 提现资金链路真库 smoke（P4-C）。env-gated：仅当 WALLET_DB_SMOKE=1 时运行，
 * 普通 `macosArm64Test` 跳过。需要：
 *   - privchat-application 库可达（V003/V004/V005 已迁移）
 *   - NETON_WALLET_CARD_ENC_KEY 设为 32B base64
 *   - 跑前后用 psql 清 TEST_UID 数据（见 runbook）
 *
 * 覆盖 GPT 验证点：freeze/deduct/unfreeze + 幂等 + 负向用例 + 银行卡 masked/reveal/软删。
 */
@OptIn(ExperimentalEncodingApi::class)
class WalletWithdrawDbSmokeTest {

    private val TEST_UID = 990000001L
    private val OTHER_UID = 990000002L

    @Test
    fun moneyChainSmoke() {
        if (getEnv("WALLET_DB_SMOKE") != "1") return  // 普通测试跳过
        val keyB64 = getEnv(EnvWalletCryptoKeyProvider.ENV_KEY_NAME)
            ?: Base64.encode(ByteArray(32) { (it + 3).toByte() })

        SqlxDatabase.initialize(
            DatabaseConfig(
                driver = DatabaseDriver.POSTGRESQL,
                uri = getEnv("WALLET_DB_URI")
                    ?: "postgresql://zoujiaqing:privchat@localhost:5432/privchat-application",
            )
        )

        val crypto = BankCardCrypto(EnvWalletCryptoKeyProvider(keyB64), ByteArray(32) { (it + 9).toByte() })
        val payWallet = PayWalletLogic(NoopLogger)
        val withdraw = WalletWithdrawLogic(NoopLogger)
        val cards = UserBankCardLogic(NoopLogger, crypto)

        runBlocking {
            // ---- seed: 钱包 balance=100000, freeze=0 ----
            val wallet = PayWalletTable.insert(PayWallet(userId = TEST_UID, balance = 100_000))
            assertEquals(0L, wallet.freezePrice)

            // ========== 1. 银行卡链路 ==========
            val view = cards.bindBankCard(TEST_UID, "张三", "招商银行", "CMB", "6225 7600 1234 5678")
            assertEquals("**** **** **** 5678", view.cardNoMasked)
            // DB 无明文卡号
            val stored = UserBankCardTable.get(view.id)!!
            assertTrue(!stored.cardNoCiphertext.contains("6225"))
            assertEquals("**** **** **** 5678", stored.cardNoMasked)
            // list 只回 masked（view 类型本身不含密文字段）
            assertEquals(1, cards.listMyBankCards(TEST_UID).size)
            // reveal 出完整卡号
            assertEquals("6225760012345678", cards.adminRevealCardNo(OperatorContext.of(1), view.id))
            val cardId = view.id
            // 不可抵赖：reveal 落 DB 敏感审计行（P0/V007）
            val rev = sensitiveAudit("BANK_CARD_REVEAL", cardId)
            assertNotNull(rev, "reveal 写 pay_sensitive_audit_logs")
            assertEquals(1L, rev.operatorId, "reveal 审计 operatorId")
            assertEquals(TEST_UID, rev.targetUserId, "reveal 审计 targetUserId")

            // ========== 2. 创建提现：冻结 ==========
            val o1 = withdraw.createWithdrawOrder(OperatorContext.of(TEST_UID),cardId, 30_000)
            assertEquals(WithdrawStateMachine.PENDING, o1.status)
            var w = PayWalletTable.get(wallet.id)!!
            assertEquals(100_000L, w.balance, "balance 不变")
            assertEquals(30_000L, w.freezePrice, "freeze += amount")
            // ledger: WITHDRAW_FREEZE(300, orderId)
            assertNotNull(ledger(300, o1.id), "WITHDRAW_FREEZE 写入")

            // 幂等：再 freeze 同 order 不重复扣冻
            payWallet.freeze(wallet.id, 30_000, o1.id, "dup")
            assertEquals(30_000L, PayWalletTable.get(wallet.id)!!.freezePrice, "重复 freeze 不叠加")

            // ========== 3. 审核通过：资金不变 ==========
            val o1a = withdraw.approve(OperatorContext.of(1), o1.id, "ok")
            assertEquals(WithdrawStateMachine.APPROVED, o1a.status)
            w = PayWalletTable.get(wallet.id)!!
            assertEquals(100_000L, w.balance)
            assertEquals(30_000L, w.freezePrice)
            assertTrue(auditCount(o1.id) >= 2, "create+approve audit")

            // ========== 4. 标记已打款：从冻结实扣 ==========
            val o1p = withdraw.markPaid(OperatorContext.of(1), o1.id, "T123")
            assertEquals(WithdrawStateMachine.PAID, o1p.status)
            w = PayWalletTable.get(wallet.id)!!
            assertEquals(70_000L, w.balance, "balance -= amount")
            assertEquals(0L, w.freezePrice, "freeze -= amount")
            assertNotNull(ledger(302, o1.id), "WITHDRAW_DEDUCT 写入")
            // 幂等：重复 markPaid 被状态机拒（已 PAID）
            assertFailsWith<HttpException> { withdraw.markPaid(OperatorContext.of(1), o1.id, "T123") }

            // ========== 5. 第二单：驳回解冻 ==========
            val o2 = withdraw.createWithdrawOrder(OperatorContext.of(TEST_UID),cardId, 20_000)
            assertEquals(20_000L, PayWalletTable.get(wallet.id)!!.freezePrice)
            val o2r = withdraw.reject(OperatorContext.of(1), o2.id, "信息不符")
            assertEquals(WithdrawStateMachine.REJECTED, o2r.status)
            w = PayWalletTable.get(wallet.id)!!
            assertEquals(70_000L, w.balance, "balance 不变")
            assertEquals(0L, w.freezePrice, "unfreeze")
            assertNotNull(ledger(301, o2.id), "WITHDRAW_UNFREEZE 写入")

            // ========== 6. 负向用例 ==========
            // 余额不足（available=70000，提 80000）
            assertFailsWith<HttpException> { withdraw.createWithdrawOrder(OperatorContext.of(TEST_UID),cardId, 80_000) }
            // 非本人银行卡
            assertFailsWith<HttpException> { withdraw.createWithdrawOrder(OperatorContext.of(OTHER_UID), cardId, 1_000) }
            // 已软删银行卡
            assertTrue(cards.deleteBankCard(TEST_UID, cardId))
            assertEquals(0, cards.listMyBankCards(TEST_UID).size, "软删后 list 不返回")
            assertFailsWith<HttpException> { withdraw.createWithdrawOrder(OperatorContext.of(TEST_UID),cardId, 1_000) }
            // ========== 7. 终态订单：所有动钱操作无副作用（P0 状态矩阵 DB 级）==========
            // o1 已 PAID（终态）：approve/reject/markPaid/markFailed/cancel 全非法，
            // 且必须不改 balance/freeze、不新增 ledger/audit。
            run {
                val op = OperatorContext.of(TEST_UID)
                val balBefore = PayWalletTable.get(wallet.id)!!.balance
                val frzBefore = PayWalletTable.get(wallet.id)!!.freezePrice
                val ledgerBefore = ledgerCount(wallet.id)
                val auditBefore = auditCount(o1.id)
                assertFailsWith<HttpException> { withdraw.approve(op, o1.id, null) }
                assertFailsWith<HttpException> { withdraw.reject(op, o1.id, "x") }
                assertFailsWith<HttpException> { withdraw.markPaid(op, o1.id, "x") }
                assertFailsWith<HttpException> { withdraw.markFailed(op, o1.id, "x") }
                assertFailsWith<HttpException> { withdraw.cancel(op, o1.id) }
                val w2 = PayWalletTable.get(wallet.id)!!
                assertEquals(balBefore, w2.balance, "终态非法操作不改 balance")
                assertEquals(frzBefore, w2.freezePrice, "终态非法操作不改 freeze")
                assertEquals(ledgerBefore, ledgerCount(wallet.id), "终态非法操作不新增 ledger")
                assertEquals(auditBefore, auditCount(o1.id), "终态非法操作不新增 audit")
            }

            println("[WALLET_DB_SMOKE] PASS: freeze/deduct/unfreeze/idempotency/negative/state-matrix all verified")
        }
    }

    private suspend fun sensitiveAudit(action: String, targetId: Long): PaySensitiveAuditLog? =
        PaySensitiveAuditLogTable.oneWhere {
            and(PaySensitiveAuditLog::action eq action, PaySensitiveAuditLog::targetId eq targetId)
        }

    private suspend fun ledger(bizType: Int, bizId: Long): PayWalletTransaction? =
        PayWalletTransactionTable.oneWhere {
            and(PayWalletTransaction::bizType eq bizType, PayWalletTransaction::bizId eq bizId)
        }

    private suspend fun ledgerCount(walletId: Long): Int =
        PayWalletTransactionTable.query {
            where { PayWalletTransaction::walletId eq walletId }
        }.list().size

    private suspend fun auditCount(orderId: Long): Int =
        WalletWithdrawAuditLogTable.query {
            where { WalletWithdrawAuditLog::orderId eq orderId }
        }.list().size
}
