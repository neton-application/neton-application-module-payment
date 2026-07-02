package logic

import dto.PageResponse
import model.MoneyTransferOrder
import model.PaySensitiveAuditLog
import table.MoneyTransferOrderTable
import table.PaySensitiveAuditLogTable
import neton.database.api.DbContext
import neton.database.dbContext
import neton.database.dsl.*
import neton.logging.Logger

/**
 * 转账（RP-2，PrivChat Money Message）。无需接收确认：一事务内完成发送方扣款(500) +
 * 接收方入账(501) + 订单 + 审计。成功即终态。资金真相在订单/双边 ledger/audit；
 * 消息只搬运引用。消息发送失败不回滚资金（money-first，见设计 §6）。
 */
@neton.core.annotations.Logic(logger = "logic.money-transfer")
class MoneyTransferLogic(
    private val log: Logger,
    private val db: DbContext = dbContext(),
) {
    private val wallet = PayWalletLogic(log, db)

    companion object {
        const val STATUS_SUCCESS = 0
        const val STATUS_REFUNDED = 1
    }

    private fun now() = kotlin.time.Clock.System.now().toEpochMilliseconds()

    /** 转账：校验发送方 available≥amount，一事务内扣发送方 + 入账接收方（懒建收方钱包）+ 审计。 */
    suspend fun transfer(
        fromUserId: Long,
        toUserId: Long,
        channelId: String,
        amount: Long,
        remark: String?,
    ): MoneyTransferOrder = db.transaction {
        requireParam(amount > 0) { "amount must be positive: $amount" }
        requireParam(fromUserId != toUserId) { "cannot transfer to self" }
        val fromWallet = wallet.getWalletByUserId(fromUserId)
            ?: walletNotFound("wallet not found for user $fromUserId")

        val order = MoneyTransferOrderTable.insert(
            MoneyTransferOrder(
                fromUserId = fromUserId,
                toUserId = toUserId,
                channelId = channelId,
                amount = amount,
                remark = remark,
                status = STATUS_SUCCESS,
                createdAt = now(),
            )
        )
        // 扣发送方(500) → 入账接收方(501)，同一事务，biz_id=transfer_id。
        wallet.applyMoneyMoveInTx(fromWallet.id, -amount, PayWalletLogic.BIZ_TYPE_TRANSFER_OUT, order.id, "transfer_out")
        val toWallet = wallet.getOrCreateWalletInTx(toUserId)
        wallet.applyMoneyMoveInTx(toWallet.id, amount, PayWalletLogic.BIZ_TYPE_TRANSFER_IN, order.id, "transfer_in")
        audit(order.id, fromUserId, toUserId, amount)
        log.info("money_transfer.done", mapOf("id" to order.id, "from" to fromUserId, "to" to toUserId, "amount" to amount))
        order
    }

    suspend fun detail(transferId: Long): MoneyTransferOrder? = MoneyTransferOrderTable.get(transferId)

    suspend fun pageMine(userId: Long, page: Int, size: Int): PageResponse<MoneyTransferOrder> {
        val result = MoneyTransferOrderTable.query {
            where { or(MoneyTransferOrder::fromUserId eq userId, MoneyTransferOrder::toUserId eq userId) }
            orderBy(MoneyTransferOrder::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    private suspend fun audit(transferId: Long, fromUserId: Long, toUserId: Long, amount: Long) {
        PaySensitiveAuditLogTable.insert(
            PaySensitiveAuditLog(
                operatorId = fromUserId,
                operatorRole = "user",
                action = "MONEY_TRANSFER",
                targetType = "MONEY_TRANSFER",
                targetId = transferId,
                targetUserId = toUserId,
                reason = "amount=$amount from=$fromUserId to=$toUserId",
            )
        )
    }
}
