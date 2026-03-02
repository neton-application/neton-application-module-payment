package table

import model.PayWallet
import model.PayWalletTableImpl
import neton.database.api.Table

object PayWalletTable : Table<PayWallet, Long> by PayWalletTableImpl
