package table

import model.PayChannel
import model.PayChannelTableImpl
import neton.database.api.Table

object PayChannelTable : Table<PayChannel, Long> by PayChannelTableImpl
