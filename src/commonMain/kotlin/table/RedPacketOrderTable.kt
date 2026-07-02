package table

import model.RedPacketOrder
import model.RedPacketOrderTableImpl
import neton.database.api.Table

object RedPacketOrderTable : Table<RedPacketOrder, Long> by RedPacketOrderTableImpl
