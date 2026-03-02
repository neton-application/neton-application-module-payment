package table

import model.PayRefund
import model.PayRefundTableImpl
import neton.database.api.Table

object PayRefundTable : Table<PayRefund, Long> by PayRefundTableImpl
