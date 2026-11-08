package logic

import dto.PageResponse
import model.ConsumptionAccount
import model.ConsumptionTransaction
import neton.database.api.DbContext
import neton.database.dsl.*
import table.ConsumptionAccountTable
import table.ConsumptionTransactionTable

@neton.core.annotations.Logic
class ConsumptionBalanceLogic(private val db: DbContext) {
    suspend fun account(userId: Long): ConsumptionAccount {
        ConsumptionAccountTable.oneWhere { ConsumptionAccount::userId eq userId }?.let { return it }
        db.execute(
            "INSERT INTO pay_consumption_accounts (user_id) VALUES (:uid) ON CONFLICT (user_id) DO NOTHING",
            mapOf("uid" to userId),
        )
        return checkNotNull(ConsumptionAccountTable.oneWhere { ConsumptionAccount::userId eq userId })
    }

    suspend fun accounts(userIds: List<Long>): Map<Long, ConsumptionAccount> =
        if (userIds.isEmpty()) emptyMap() else ConsumptionAccountTable.query {
            where { ConsumptionAccount::userId `in` userIds }
        }.list().associateBy { it.userId }

    suspend fun transactions(userId: Long): List<ConsumptionTransaction> =
        ConsumptionTransactionTable.query {
            where { ConsumptionTransaction::userId eq userId }
        }.list()

    suspend fun transactionPage(userId: Long, page: Int, size: Int): PageResponse<ConsumptionTransaction> {
        val result = ConsumptionTransactionTable.query {
            where { ConsumptionTransaction::userId eq userId }
            orderBy(ConsumptionTransaction::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size, result.totalPages)
    }

    /** 调用方在同一事务内维护业务预留状态。 */
    suspend fun reserveInTx(userId: Long, amount: Long): Boolean {
        require(amount >= 0)
        account(userId)
        return db.execute(
            "UPDATE pay_consumption_accounts SET reserved_balance = reserved_balance + :amount, version = version + 1 " +
                "WHERE user_id = :uid AND CAST(balance AS numeric) - reserved_balance >= :amount",
            mapOf("uid" to userId, "amount" to amount),
        ) == 1L
    }

    suspend fun lockInTx(userId: Long) {
        db.execute("SELECT id FROM pay_consumption_accounts WHERE user_id = :uid FOR UPDATE", mapOf("uid" to userId))
    }

    suspend fun releaseInTx(userId: Long, amount: Long) {
        require(amount >= 0)
        db.execute(
            "UPDATE pay_consumption_accounts SET reserved_balance = GREATEST(reserved_balance - :amount, 0) WHERE user_id = :uid",
            mapOf("uid" to userId, "amount" to amount),
        )
    }

    suspend fun applyDelta(userId: Long, delta: Long, type: String, ref: String?): Long =
        db.transaction { applyDeltaInTx(userId, delta, type, ref) }

    /** 余额增量与流水原子提交。业务可在同一事务内提交自己的用量或订单。 */
    suspend fun applyDeltaInTx(
        userId: Long,
        delta: Long,
        type: String,
        ref: String?,
        operatorId: Long? = null,
        remark: String? = null,
    ): Long {
        val account = account(userId)
        lockInTx(userId)
        if (ref != null) {
            val inserted = db.execute(
                "INSERT INTO pay_consumption_transactions " +
                    "(user_id, type, amount, balance_after, ref, created_at, operator_id, remark) " +
                    "VALUES (:uid, :type, :amount, 0, :ref, :ts, :operator, :remark) " +
                    "ON CONFLICT (ref) WHERE ref IS NOT NULL DO NOTHING",
                mapOf("uid" to userId, "type" to type, "amount" to delta, "ref" to ref,
                    "ts" to kotlin.time.Clock.System.now().toEpochMilliseconds(), "operator" to operatorId, "remark" to remark),
            )
            if (inserted == 0L) {
                if (operatorId != null) {
                    val prior = ConsumptionTransactionTable.oneWhere { ConsumptionTransaction::ref eq ref }
                    if (prior == null || prior.userId != userId || prior.type != type || prior.amount != delta || prior.remark != remark) {
                        walletConflict(PaymentErrorCodes.CONSUMPTION_REF_CONFLICT)
                    }
                }
                return account(userId).balance
            }
        }
        if (operatorId != null) {
            val current = account(userId).balance
            val next = current + delta
            requireParam(((current xor next) and (delta xor next)) >= 0, PaymentErrorCodes.AMOUNT_INVALID, "amount")
        }
        check(db.execute(
            "UPDATE pay_consumption_accounts SET balance = balance + :amount, version = version + 1 WHERE id = :id",
            mapOf("amount" to delta, "id" to account.id),
        ) == 1L)
        val balance = account(userId).balance
        if (ref == null) {
            ConsumptionTransactionTable.insert(ConsumptionTransaction(userId = userId, type = type, amount = delta, balanceAfter = balance))
        } else {
            db.execute("UPDATE pay_consumption_transactions SET balance_after = :balance WHERE ref = :ref",
                mapOf("balance" to balance, "ref" to ref))
        }
        return balance
    }
}
