package table

import model.PayTransfer
import model.PayTransferTableImpl
import neton.database.api.Table

object PayTransferTable : Table<PayTransfer, Long> by PayTransferTableImpl
