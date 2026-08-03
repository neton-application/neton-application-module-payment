package table

import model.PayWalletFreeze
import model.PayWalletFreezeTableImpl
import neton.database.api.Table

object PayWalletFreezeTable : Table<PayWalletFreeze, Long> by PayWalletFreezeTableImpl
