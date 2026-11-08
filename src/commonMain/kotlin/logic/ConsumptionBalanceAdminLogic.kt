package logic

import dto.PageResponse
import kotlinx.serialization.Serializable
import neton.database.api.DbContext

@Serializable
data class ConsumptionBalanceUserVO(
    val userId: String,
    val username: String?,
    val balance: String,
    val reservedBalance: String,
)

@Serializable
data class ConsumptionBalanceMoveRequest(
    val userId: String,
    val amount: String,
    val ref: String,
    val remark: String,
)

@Serializable
data class ConsumptionBalanceTransactionVO(
    val id: String,
    val type: String,
    val amount: String,
    val balanceAfter: String,
    val ref: String?,
    val createdAt: Long?,
    val operatorId: String?,
    val remark: String?,
)

enum class ConsumptionBalanceMove(val ledgerType: String) {
    MANUAL_CREDIT("manual_credit"),
    GIFT("gift"),
    REVOKE("revoke"),
}

@neton.core.annotations.Logic
class ConsumptionBalanceAdminLogic(
    private val db: DbContext,
    private val accounts: AccountLogic,
    private val balances: ConsumptionBalanceLogic,
) {
    suspend fun page(page: Int, size: Int, userId: String?, username: String?): PageResponse<ConsumptionBalanceUserVO> {
        val uid = userId?.trim()?.takeIf { it.isNotEmpty() }
        val id = uid?.toLongOrNull()
        if (uid != null && (id == null || id <= 0)) return PageResponse(emptyList(), 0, page, size, 0)
        val users = accounts.page(page, size, uid = id, username = username?.trim())
        val amounts = balances.accounts(users.list.map { it.id })
        return PageResponse(users.list.map { user ->
            val balance = amounts[user.id]
            ConsumptionBalanceUserVO(user.id.toString(), user.username,
                (balance?.balance ?: 0L).toString(), (balance?.reservedBalance ?: 0L).toString())
        }, users.total, users.page, users.size, users.totalPages)
    }

    suspend fun transactionPage(userId: String, page: Int, size: Int): PageResponse<ConsumptionBalanceTransactionVO> {
        val result = balances.transactionPage(requireUser(userId), page, size)
        return PageResponse(result.list.map {
            ConsumptionBalanceTransactionVO(it.id.toString(), it.type, it.amount.toString(), it.balanceAfter.toString(),
                it.ref, it.createdAt, it.operatorId?.toString(), it.remark)
        }, result.total, result.page, result.size, result.totalPages)
    }

    suspend fun move(operatorId: Long, move: ConsumptionBalanceMove, request: ConsumptionBalanceMoveRequest) {
        val amount = request.amount.toLongOrNull()?.takeIf { it > 0 }
            ?: walletBadRequest(PaymentErrorCodes.AMOUNT_INVALID, "amount")
        val ref = request.ref.trim()
        requireParam(ref.length in 1..122, PaymentErrorCodes.CONSUMPTION_REF_INVALID, "ref")
        val remark = request.remark.trim()
        requireParam(remark.length in 1..500, PaymentErrorCodes.REMARK_REQUIRED, "remark")
        val delta = if (move == ConsumptionBalanceMove.REVOKE) -amount else amount
        db.transaction {
            val userId = requireUser(request.userId)
            balances.applyDeltaInTx(userId, delta, move.ledgerType, "admin:$ref", operatorId, remark)
        }
    }

    private suspend fun requireUser(value: String): Long {
        val user = value.toLongOrNull()?.takeIf { it > 0 }?.let { accounts.get(it) }
            ?: walletNotFound(PaymentErrorCodes.CONSUMPTION_USER_NOT_FOUND)
        return user.id
    }
}
