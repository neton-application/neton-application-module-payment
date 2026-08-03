package smoke

import model.PayWallet
import model.PayWalletFreeze
import model.PayWalletTransaction
import model.WalletWithdrawOrder
import neton.core.config.getEnv
import neton.database.adapter.sqlx.SqlxDatabase
import neton.database.config.DatabaseConfig
import neton.database.config.DatabaseDriver
import neton.database.dsl.*
import table.PayWalletFreezeTable
import table.PayWalletTable
import table.PayWalletTransactionTable
import table.WalletWithdrawOrderTable

/**
 * DB smoke 共用的一次性初始化。
 *
 * `SqlxDatabase.initialize` 每进程只能调一次（第二次直接
 * `Database is already initialized`）。以前每个 smoke 文件各调各的，靠「同一时刻只开
 * 一个 smoke 开关」才没撞上——两个开关一起打开，先跑的那个把库初始化掉，
 * 后跑的当场失败，而且失败信息跟被测逻辑毫无关系。这里收敛成一个入口。
 */
object SmokeDatabase {
    private var initialized = false

    fun ensure() {
        if (initialized) return
        SqlxDatabase.initialize(
            DatabaseConfig(
                driver = DatabaseDriver.POSTGRESQL,
                uri = getEnv("WALLET_DB_URI")
                    ?: "postgresql://zoujiaqing:privchat@localhost:5432/privchat-application",
            )
        )
        initialized = true
    }

    /**
     * 清掉某个测试用户的全部资金数据，让 smoke 可重复跑。
     *
     * **必须先删子行再删钱包**：只删 `pay_wallets` 的话，账变和提现单会变成
     * 指向不存在钱包的孤儿行，`wallet-consistency-check.sh` 的
     * 「no orphan ledger / no orphan withdraw order」当场报红——脏数据是测试自己造的，
     * 但看起来像生产账务坏了。
     */
    suspend fun purgeTestUser(userId: Long) {
        val walletIds = PayWalletTable.query { where { PayWallet::userId eq userId } }.list().map { it.id }
        for (id in walletIds) {
            PayWalletTransactionTable.query { where { PayWalletTransaction::walletId eq id } }.delete()
            WalletWithdrawOrderTable.query { where { WalletWithdrawOrder::walletId eq id } }.delete()
        }
        PayWalletFreezeTable.query { where { PayWalletFreeze::userId eq userId } }.delete()
        PayWalletTable.query { where { PayWallet::userId eq userId } }.delete()
    }
}
