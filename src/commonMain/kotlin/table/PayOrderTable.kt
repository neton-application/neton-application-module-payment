package table

import model.PayOrder
import model.PayOrderTableImpl
import neton.database.api.Table

object PayOrderTable : Table<PayOrder, Long> by PayOrderTableImpl
