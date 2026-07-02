package table

import model.MoneyTransferOrder
import model.MoneyTransferOrderTableImpl
import neton.database.api.Table

object MoneyTransferOrderTable : Table<MoneyTransferOrder, Long> by MoneyTransferOrderTableImpl
