package table

import model.WalletWithdrawOrder
import model.WalletWithdrawOrderTableImpl
import neton.database.api.Table

object WalletWithdrawOrderTable : Table<WalletWithdrawOrder, Long> by WalletWithdrawOrderTableImpl
