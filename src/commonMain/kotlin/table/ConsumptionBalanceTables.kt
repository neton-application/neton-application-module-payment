package table

import model.ConsumptionAccount
import model.ConsumptionAccountTableImpl
import model.ConsumptionTransaction
import model.ConsumptionTransactionTableImpl
import neton.database.api.Table

object ConsumptionAccountTable : Table<ConsumptionAccount, Long> by ConsumptionAccountTableImpl
object ConsumptionTransactionTable : Table<ConsumptionTransaction, Long> by ConsumptionTransactionTableImpl
