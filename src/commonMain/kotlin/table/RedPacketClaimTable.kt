package table

import model.RedPacketClaim
import model.RedPacketClaimTableImpl
import neton.database.api.Table

object RedPacketClaimTable : Table<RedPacketClaim, Long> by RedPacketClaimTableImpl
