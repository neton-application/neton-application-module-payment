package table

import model.PayWalletTransaction
import model.PayWalletTransactionTableImpl
import neton.database.api.Table

object PayWalletTransactionTable : Table<PayWalletTransaction, Long> by PayWalletTransactionTableImpl
