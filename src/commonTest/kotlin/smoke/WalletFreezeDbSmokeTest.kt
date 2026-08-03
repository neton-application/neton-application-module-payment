package smoke

import kotlinx.coroutines.runBlocking
import logic.OperatorContext
import logic.PayWalletLogic
import logic.WalletFreezeLogic
import logic.WalletFreezeStatus
import model.PayWallet
import model.PayWalletFreeze
import neton.core.config.getEnv
import neton.core.http.HttpException
import neton.database.dbContext
import neton.database.dsl.*
import neton.logging.Fields
import neton.logging.Logger
import table.PayWalletFreezeTable
import table.PayWalletTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object FreezeSmokeLogger : Logger {
    override fun trace(msg: String, fields: Fields) {}
    override fun debug(msg: String, fields: Fields) {}
    override fun info(msg: String, fields: Fields) {}
    override fun warn(msg: String, fields: Fields, cause: Throwable?) {}
    override fun error(msg: String, fields: Fields, cause: Throwable?) {}
}

/**
 * 冻结链路 DB smoke（spec WALLET_FREEZE_SPEC §2.1 / §3）。
 *
 * 跑法与提现 smoke 一致：`WALLET_DB_SMOKE=1 ./gradlew :...:macosArm64Test`。
 *
 * **这个文件的存在理由**是「到账即冻」那个用例：`freeze_price` 是缓存，而司法冻结的
 * 冻结额跟着余额走。入账路径不重算缓存的话，缓存偏小 = 新到的钱直接变成可用余额，
 * 冻结静默失效。本机实测踩过：全额冻结下充值 2000，balance 涨到 7584
 * 而 freeze_price 还停在 5584。纯函数测试（WalletFreezeModelTest）测不出这个——
 * 那边公式一直是对的，坏的是「谁负责把公式的结果写回去」。
 */
class WalletFreezeDbSmokeTest {

    private val TEST_UID = 990000101L

    @Test
    fun freezeChainSmoke() {
        if (getEnv("WALLET_DB_SMOKE") != "1") return  // 普通测试跳过

        SmokeDatabase.ensure()

        val payWallet = PayWalletLogic(FreezeSmokeLogger)
        val freezes = WalletFreezeLogic(FreezeSmokeLogger, dbContext())
        val op = OperatorContext.of(1)

        runBlocking {
            // 清掉上一轮的残留，否则第二次跑时 `oneWhere { userId eq }` 会撞上两行钱包。
            SmokeDatabase.purgeTestUser(TEST_UID)

            // 起始余额走入账路径而不是直接 insert 一个 balance：直接塞余额会让
            // wallet-consistency-check.sh 的「global conservation」永久欠一笔——
            // 有钱包余额却没有对应账变。测试数据不该给一致性检查制造噪音。
            val wallet = PayWalletTable.insert(PayWallet(userId = TEST_UID))
            assertEquals(0L, wallet.freezePrice)
            payWallet.manualRecharge(TEST_UID, 10_000, "smoke 期初余额")

            // ---- 1. 单笔风控冻结：金额确定，可用余额随之减少 ----
            val hold = freezes.placeRiskHold(op, TEST_UID, 3_000, refId = "SMOKE-RISK-1", reasonText = "smoke")
            assertEquals(WalletFreezeStatus.ACTIVE, hold.status)
            assertEquals(3_000L, PayWalletTable.get(wallet.id)!!.freezePrice)

            // 幂等：同 refId 再下一次不叠加。
            freezes.placeRiskHold(op, TEST_UID, 3_000, refId = "SMOKE-RISK-1", reasonText = null)
            assertEquals(3_000L, PayWalletTable.get(wallet.id)!!.freezePrice, "同 refId 重复下达不叠加")

            // 可用不足要显式失败，不能悄悄冻一个更小的数。
            assertFailsWith<HttpException> {
                freezes.placeRiskHold(op, TEST_UID, 9_999_999, refId = "SMOKE-RISK-TOOBIG", reasonText = null)
            }

            // ---- 2. 全额司法冻结：吸收剩余全部余额，与风控冻结不重复计算 ----
            val judicial = freezes.placeJudicialFreeze(
                op, TEST_UID, targetAmount = null, legalDocNo = "SMOKE-DOC-1", reasonText = "smoke", expiresAt = 0,
            )
            assertEquals(10_000L, PayWalletTable.get(wallet.id)!!.freezePrice, "全额冻结 = 全部余额")

            // ---- 3. 到账即冻（本文件的核心用例）----
            payWallet.manualRecharge(TEST_UID, 2_000, "smoke 到账即冻")
            val afterCredit = PayWalletTable.get(wallet.id)!!
            assertEquals(12_000L, afterCredit.balance)
            assertEquals(12_000L, afterCredit.freezePrice, "入账必须被司法冻结吸收，可用余额仍为 0")

            // 可用为 0 时任何借记都必须被挡住。
            assertFailsWith<IllegalArgumentException> {
                payWallet.updateBalance(wallet.id, -1, PayWalletLogic.BIZ_TYPE_ADMIN_ADJUST, 0, "smoke 借记")
            }

            // ---- 4. 解除司法冻结：只退回它自己吸收的那部分 ----
            freezes.release(op, judicial.id)
            assertEquals(3_000L, PayWalletTable.get(wallet.id)!!.freezePrice, "解除司法冻结后只剩风控冻结")

            // 解除时把当时真实冻住的钱（12000 余额 − 3000 风控冻结）快照进记录，
            // 否则这条历史事后再也算不出金额，用户只会看到「账户冻结 ¥0.00 已解除」。
            val finished = PayWalletFreezeTable.get(judicial.id)!!
            assertEquals(WalletFreezeStatus.RELEASED, finished.status)
            assertEquals(9_000L, finished.amount, "全额司法冻结结束时快照真实冻结额")

            freezes.release(op, hold.id)
            assertEquals(0L, PayWalletTable.get(wallet.id)!!.freezePrice)

            println("[WALLET_DB_SMOKE] PASS: risk-hold/idempotency/judicial/credit-absorption/release all verified")
        }
    }
}
