package logic

import dto.PageResponse
import model.PayWallet
import model.PayWalletTransaction
import model.PayWalletRecharge
import table.PayWalletTable
import table.PayWalletTransactionTable
import table.PayWalletRechargeTable
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

class PayWalletLogic(
    private val log: Logger,
    private val db: DbContext = dbContext()
) {

    suspend fun getWallet(userId: Long): PayWallet? {
        return PayWalletTable.oneWhere {
            PayWallet::userId eq userId
        }
    }

    suspend fun getWalletById(id: Long): PayWallet? {
        return PayWalletTable.get(id)
    }

    suspend fun getWalletByUserId(userId: Long): PayWallet? {
        return PayWalletTable.oneWhere { PayWallet::userId eq userId }
    }

    suspend fun adjustBalance(userId: Long, balance: Long) {
        val wallet = PayWalletTable.oneWhere { PayWallet::userId eq userId }
            ?: throw IllegalArgumentException("Wallet not found for userId: $userId")
        val diff = balance - wallet.balance
        updateBalance(wallet.id, diff, 200, 0L, "Admin adjust")
    }

    suspend fun updateBalance(walletId: Long, price: Long, bizType: Int, bizId: Long, title: String) {
        val wallet = PayWalletTable.get(walletId)
            ?: throw IllegalArgumentException("Wallet not found: $walletId")

        val newBalance = wallet.balance + price
        if (newBalance < 0) {
            throw IllegalArgumentException("Insufficient balance for wallet: $walletId")
        }

        // Update wallet balance
        val updatedWallet = if (price > 0) {
            wallet.copy(
                balance = newBalance,
                totalRecharge = wallet.totalRecharge + price
            )
        } else {
            wallet.copy(
                balance = newBalance,
                totalExpense = wallet.totalExpense + (-price)
            )
        }
        PayWalletTable.update(updatedWallet)

        // Create transaction record
        val transaction = PayWalletTransaction(
            walletId = walletId,
            bizType = bizType,
            bizId = bizId,
            title = title,
            price = price,
            balance = newBalance
        )
        PayWalletTransactionTable.insert(transaction)
        log.info("Updated wallet balance: walletId=$walletId, price=$price, newBalance=$newBalance")
    }

    suspend fun pageWallets(
        page: Int,
        size: Int,
        userId: Long? = null
    ): PageResponse<PayWallet> {
        val result = PayWalletTable.query {
            where {
                whenPresent(userId) { PayWallet::userId eq it }
            }
            orderBy(PayWallet::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun pageTransactions(
        page: Int,
        size: Int,
        walletId: Long? = null,
        bizType: Int? = null
    ): PageResponse<PayWalletTransaction> {
        val result = PayWalletTransactionTable.query {
            where {
                and(
                    whenPresent(walletId) { PayWalletTransaction::walletId eq it },
                    whenPresent(bizType) { PayWalletTransaction::bizType eq it }
                )
            }
            orderBy(PayWalletTransaction::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun recharge(recharge: PayWalletRecharge): Long {
        val inserted = PayWalletRechargeTable.insert(recharge)
        log.info("Created wallet recharge with id: ${inserted.id}, walletId: ${recharge.walletId}")
        return inserted.id
    }

    suspend fun pageRecharges(
        page: Int,
        size: Int,
        walletId: Long? = null,
        payStatus: Int? = null
    ): PageResponse<PayWalletRecharge> {
        val result = PayWalletRechargeTable.query {
            where {
                and(
                    whenPresent(walletId) { PayWalletRecharge::walletId eq it },
                    whenPresent(payStatus) { PayWalletRecharge::payStatus eq it }
                )
            }
            orderBy(PayWalletRecharge::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun getRecharge(id: Long): PayWalletRecharge? {
        return PayWalletRechargeTable.get(id)
    }

    suspend fun updateRecharge(recharge: PayWalletRecharge) {
        PayWalletRechargeTable.update(recharge)
        log.info("Updated wallet recharge with id: ${recharge.id}")
    }

    suspend fun getTransactionSummary(walletId: Long): Pair<Long, Long> {
        val transactions = PayWalletTransactionTable.query {
            where {
                PayWalletTransaction::walletId eq walletId
            }
        }.list()

        val totalIncome = transactions.filter { it.price > 0 }.sumOf { it.price }
        val totalExpense = transactions.filter { it.price < 0 }.sumOf { -it.price }
        return Pair(totalIncome, totalExpense)
    }
}
