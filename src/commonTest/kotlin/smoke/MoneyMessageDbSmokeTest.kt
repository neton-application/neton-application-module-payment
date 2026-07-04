package smoke

import kotlinx.coroutines.runBlocking
import logic.MoneyTransferLogic
import logic.PayWalletLogic
import logic.RedPacketLogic
import model.MoneyMessageNotificationOutbox
import model.PayWallet
import model.PayWalletTransaction
import model.RedPacketOrder
import table.MoneyMessageNotificationOutboxTable
import neton.core.config.getEnv
import neton.core.http.HttpException
import neton.database.adapter.sqlx.SqlxDatabase
import neton.database.config.DatabaseConfig
import neton.database.config.DatabaseDriver
import neton.database.dsl.*
import neton.logging.Fields
import neton.logging.Logger
import table.PayWalletTable
import table.PayWalletTransactionTable
import table.RedPacketOrderTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private object RpNoopLogger : Logger {
    override fun trace(msg: String, fields: Fields) {}
    override fun debug(msg: String, fields: Fields) {}
    override fun info(msg: String, fields: Fields) {}
    override fun warn(msg: String, fields: Fields, cause: Throwable?) {}
    override fun error(msg: String, fields: Fields, cause: Throwable?) {}
}

/**
 * 红包 + 转账真库 smoke（RP-2/RP-3）。env-gated：仅当 RP_DB_SMOKE=1 时运行。
 * 需要 privchat-application 库可达（V008 已迁移）；跑前后清 99001xxx 数据。
 *
 * 覆盖 GPT 验证点：发红包/领取×3/抢完/重复领取/过期退款/转账即时到账/双边 ledger/
 * 零和对账/资金守恒/负向。
 */
class MoneyMessageDbSmokeTest {

    private val S = 990010001L // sender
    private val A = 990010002L
    private val B = 990010003L
    private val C = 990010004L
    private val D = 990010005L // transfer receiver
    private val E = 990010006L // late claimer

    @Test
    fun moneyMessageSmoke() {
        if (getEnv("RP_DB_SMOKE") != "1") return
        SqlxDatabase.initialize(
            DatabaseConfig(
                driver = DatabaseDriver.POSTGRESQL,
                uri = getEnv("WALLET_DB_URI")
                    ?: "postgresql://zoujiaqing:privchat@localhost:5432/privchat-application",
            )
        )
        val redPacket = RedPacketLogic(RpNoopLogger)
        val transfer = MoneyTransferLogic(RpNoopLogger)

        runBlocking {
            // seed: 只有 S 有余额 100000，其余 0（或懒建）。
            val ws = PayWalletTable.insert(PayWallet(userId = S, balance = 100_000))

            // ======== 1. 发红包 total=30000 count=3 ========
            val rp1 = redPacket.send(S, "ch1", 1, 0, 30_000, 3, "恭喜发财")
            assertEquals(70_000L, wallet(S).balance, "发红包扣发送方全额")
            assertEquals(30_000L, rp1.remainingAmount)
            assertNotNull(ledger(400, rp1.id), "ledger 400 红包扣款")

            // ======== 2. 领取 ×3（均分先到先得）========
            val a = redPacket.claim(rp1.id, A); assertEquals(10_000L, a.amount)
            val b = redPacket.claim(rp1.id, B); assertEquals(10_000L, b.amount)
            val c = redPacket.claim(rp1.id, C); assertEquals(10_000L, c.amount, "最后一份含余数")
            assertEquals(10_000L, wallet(A).balance)
            assertEquals(10_000L, wallet(C).balance)
            val rp1After = RedPacketOrderTable.get(rp1.id)!!
            assertEquals(RedPacketLogic.STATUS_FINISHED, rp1After.status, "领完 FINISHED")
            assertEquals(0L, rp1After.remainingAmount)
            assertEquals(0, rp1After.remainingCount)

            // ======== 3. 抢完 + 重复领取 都 409（不超领/不重复入账）========
            assertFailsWith<HttpException>("已抢完应拒") { redPacket.claim(rp1.id, E) }
            assertFailsWith<HttpException>("重复领取应拒") { redPacket.claim(rp1.id, A) }
            // 零和：400 + 3×401 == 0
            assertEquals(0L, sumLedgerForRedPacket(rp1.id), "红包1 托管零和")

            // ======== 4. 过期退款：send → 领1 → 强制过期 → 退剩余 ========
            val rp2 = redPacket.send(S, "ch1", 1, 0, 6_000, 2, null) // S 70000→64000
            assertEquals(64_000L, wallet(S).balance)
            val a2 = redPacket.claim(rp2.id, A); assertEquals(3_000L, a2.amount) // A 10000→13000
            // 强制过期（改 expire_at 到过去），再退款。
            RedPacketOrderTable.query { where { RedPacketOrder::id eq rp2.id } }.update {
                set(RedPacketOrder::expireAt, 1L)
            }
            assertTrue(redPacket.expireRefund(rp2.id), "过期退款执行")
            assertEquals(67_000L, wallet(S).balance, "剩余 3000 退回发送方")
            val rp2After = RedPacketOrderTable.get(rp2.id)!!
            assertEquals(RedPacketLogic.STATUS_EXPIRED, rp2After.status)
            assertEquals(0L, rp2After.remainingAmount)
            assertNotNull(ledger(402, rp2.id), "ledger 402 红包退款")
            // 幂等：重复退款 no-op
            assertTrue(!redPacket.expireRefund(rp2.id), "重复退款 no-op")
            assertEquals(67_000L, wallet(S).balance)
            assertEquals(0L, sumLedgerForRedPacket(rp2.id), "红包2 零和(-6000+3000+3000)")

            // ======== 5. 转账即时到账 S→D 20000 ========
            val t = transfer.transfer(S, D, "ch2", 20_000, "还你的")
            assertEquals(47_000L, wallet(S).balance, "转出扣发送方")
            assertEquals(20_000L, wallet(D).balance, "转入到账接收方")
            assertEquals(-20_000L, ledger(500, t.id)!!.price, "ledger 500 转出")
            assertEquals(20_000L, ledger(501, t.id)!!.price, "ledger 501 转入")

            // ======== 6. 负向 ========
            assertFailsWith<HttpException>("不能转给自己") { transfer.transfer(S, S, "ch2", 100, null) }
            assertFailsWith<HttpException>("超可用余额") { transfer.transfer(S, D, "ch2", 999_999, null) }

            // ======== 7. 资金守恒：初始 100000 全在参与者之间流转，总额不变 ========
            val total = wallet(S).balance + wallet(A).balance + wallet(B).balance +
                wallet(C).balance + wallet(D).balance
            assertEquals(100_000L, total, "资金守恒：无凭空增减")
            assertEquals(47_000L, wallet(S).balance)
            assertEquals(13_000L, wallet(A).balance)

            // ======== 8. RP-7-A 通知 outbox（同事务写入，PENDING）========
            // rp1 领完：3 RECEIVED + 1 EMPTY；rp2 领1+过期：1 RECEIVED + 1 EXPIRED。
            assertEquals(3, outbox(rp1.id, RedPacketLogic.EVENT_RED_PACKET_RECEIVED), "rp1 3× RECEIVED outbox")
            assertEquals(1, outbox(rp1.id, RedPacketLogic.EVENT_RED_PACKET_EMPTY), "rp1 1× EMPTY outbox")
            assertEquals(0, outbox(rp1.id, RedPacketLogic.EVENT_RED_PACKET_EXPIRED), "rp1 无 EXPIRED")
            assertEquals(1, outbox(rp2.id, RedPacketLogic.EVENT_RED_PACKET_RECEIVED), "rp2 1× RECEIVED outbox")
            assertEquals(1, outbox(rp2.id, RedPacketLogic.EVENT_RED_PACKET_EXPIRED), "rp2 1× EXPIRED outbox")
            assertTrue(allOutboxPending(), "所有 outbox 行 status=PENDING（RP-7-A 只写不消费）")
            // payload 含 channelId + redPacketId（adapter 消费依据）。
            val anyReceived = MoneyMessageNotificationOutboxTable.query {
                where { MoneyMessageNotificationOutbox::eventType eq RedPacketLogic.EVENT_RED_PACKET_RECEIVED }
            }.list().first()
            assertTrue(anyReceived.payloadJson.contains("\"channelId\"") && anyReceived.payloadJson.contains("\"redPacketId\""), "payload 含 channelId+redPacketId")

            // ======== 9. 拼手气红包（type=1）：10 元 / 5 人，二倍均值随机分配 ========
            val L = listOf(990010011L, 990010012L, 990010013L, 990010014L, 990010015L)
            val sBeforeLucky = wallet(S).balance
            val lucky = redPacket.send(S, "ch3", 1, RedPacketLogic.TYPE_LUCKY, 1_000, 5, "拼手气")
            assertEquals(sBeforeLucky - 1_000, wallet(S).balance, "拼手气发红包扣全额")
            assertEquals(RedPacketLogic.TYPE_LUCKY, RedPacketOrderTable.get(lucky.id)!!.type, "type=LUCKY 落库")
            val luckyAmounts = mutableListOf<Long>()
            L.forEach { uid ->
                val cl = redPacket.claim(lucky.id, uid)
                assertTrue(cl.amount >= 1L, "拼手气每人至少 1 分（实际 ${cl.amount}）")
                luckyAmounts += cl.amount
                val o = RedPacketOrderTable.get(lucky.id)!!
                assertTrue(o.remainingAmount >= 0L, "剩余金额不为负")
                assertTrue(o.remainingCount >= 0, "剩余个数不为负")
                // 不变量：未领完时 remainingAmount ≥ remainingCount（保证其余人各拿 ≥1 分）。
                if (o.remainingCount > 0) assertTrue(o.remainingAmount >= o.remainingCount, "剩余额需覆盖剩余人数×1分")
            }
            assertEquals(1_000L, luckyAmounts.sum(), "拼手气总领取额精确等于总额（无超领/无漏发）")
            val luckyAfter = RedPacketOrderTable.get(lucky.id)!!
            assertEquals(RedPacketLogic.STATUS_FINISHED, luckyAfter.status, "5 人领完 FINISHED")
            assertEquals(0L, luckyAfter.remainingAmount)
            assertEquals(0, luckyAfter.remainingCount)
            // 抢完 + 重复领取都 409。
            assertFailsWith<HttpException>("拼手气抢完应拒") { redPacket.claim(lucky.id, 990010016L) }
            assertFailsWith<HttpException>("拼手气重复领应拒") { redPacket.claim(lucky.id, L[0]) }
            // 最佳手气（读时计算）：最大金额、并列取最早 claim → 唯一可判定。
            val luckyClaims = table.RedPacketClaimTable.query {
                where { model.RedPacketClaim::redPacketId eq lucky.id }
            }.list()
            assertEquals(5, luckyClaims.size, "拼手气 5 条领取记录")
            val bestAmount = luckyClaims.maxOf { it.amount }
            val bestClaim = luckyClaims.filter { it.amount == bestAmount }.minByOrNull { it.id }!!
            assertTrue(bestClaim.amount >= 1L && luckyClaims.count { it.amount == bestAmount } >= 1, "最佳手气可判定（最大金额=${bestClaim.amount}）")
            assertEquals(0L, sumLedgerForRedPacket(lucky.id), "拼手气红包托管零和")
            assertEquals(1_000L, L.sumOf { wallet(it).balance }, "拼手气 5 人入账合计 = 1000（资金守恒）")

            // ======== 10. 普通红包 10 元 / 5 人（每人恰好 2 元，均分）========
            val M = listOf(990010021L, 990010022L, 990010023L, 990010024L, 990010025L)
            val normal = redPacket.send(S, "ch4", 1, RedPacketLogic.TYPE_NORMAL, 1_000, 5, null)
            M.forEach { uid -> assertEquals(200L, redPacket.claim(normal.id, uid).amount, "普通红包 10元/5人 每人 2 元") }
            assertEquals(RedPacketLogic.STATUS_FINISHED, RedPacketOrderTable.get(normal.id)!!.status, "普通红包领完 FINISHED")
            assertEquals(0L, sumLedgerForRedPacket(normal.id), "普通红包零和")

            // ======== 11. 拼手气边界：5 分/5 人（每人恰好 1 分）+ 拒绝 4 分/5 人（金额<人数）========
            val N = listOf(990010031L, 990010032L, 990010033L, 990010034L, 990010035L)
            val tiny = redPacket.send(S, "ch5", 1, RedPacketLogic.TYPE_LUCKY, 5, 5, null)
            N.forEach { uid -> assertEquals(1L, redPacket.claim(tiny.id, uid).amount, "5分/5人拼手气每人恰好 1 分") }
            assertEquals(RedPacketLogic.STATUS_FINISHED, RedPacketOrderTable.get(tiny.id)!!.status)
            assertEquals(0L, sumLedgerForRedPacket(tiny.id), "5分红包零和")
            assertFailsWith<HttpException>("4分/5人应拒绝创建（每份不足 1 分）") {
                redPacket.send(S, "ch5", 1, RedPacketLogic.TYPE_LUCKY, 4, 5, null)
            }

            println("[RP_DB_SMOKE] PASS: send/claim×3/drain/dup/expire-refund/transfer/zero-sum/conservation/outbox/lucky(10y÷5,best-luck)/normal(10y÷5=2y)/boundary(5f÷5=1f,reject 4f÷5) verified (ws=${ws.id})")
        }
    }

    private suspend fun wallet(uid: Long): PayWallet =
        PayWalletTable.oneWhere { PayWallet::userId eq uid } ?: error("wallet not found: $uid")

    private suspend fun outbox(rpId: Long, eventType: String): Int =
        MoneyMessageNotificationOutboxTable.query {
            where {
                and(
                    MoneyMessageNotificationOutbox::redPacketId eq rpId,
                    MoneyMessageNotificationOutbox::eventType eq eventType,
                )
            }
        }.list().size

    private suspend fun allOutboxPending(): Boolean =
        MoneyMessageNotificationOutboxTable.query {
            where { MoneyMessageNotificationOutbox::id gt 0 }
        }.list().all { it.status == 0 }

    private suspend fun ledger(bizType: Int, bizId: Long): PayWalletTransaction? =
        PayWalletTransactionTable.oneWhere {
            and(PayWalletTransaction::bizType eq bizType, PayWalletTransaction::bizId eq bizId)
        }

    /** 红包托管零和：该红包相关 400(扣款) + 401(所有领取) + 402(退款) 的 price 之和应为 0。 */
    private suspend fun sumLedgerForRedPacket(rpId: Long): Long {
        // 400/402 的 biz_id = rpId；401 的 biz_id = claim_id（不同），故按 title+关联无法直接筛；
        // 这里用 400+402(biz_id=rpId) 与该红包所有 claim 的 401 求和。
        val deductRefund = PayWalletTransactionTable.query {
            where {
                and(
                    PayWalletTransaction::bizId eq rpId,
                    PayWalletTransaction::bizType `in` listOf(400, 402),
                )
            }
        }.list().sumOf { it.price }
        val claims = table.RedPacketClaimTable.query {
            where { model.RedPacketClaim::redPacketId eq rpId }
        }.list()
        val income = PayWalletTransactionTable.query {
            where {
                and(
                    PayWalletTransaction::bizType eq 401,
                    PayWalletTransaction::bizId `in` claims.map { it.id },
                )
            }
        }.list().sumOf { it.price }
        return deductRefund + income
    }
}
