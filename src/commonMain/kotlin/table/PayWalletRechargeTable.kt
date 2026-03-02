package table

import model.PayWalletRecharge
import model.PayWalletRechargeTableImpl
import neton.database.api.Table

object PayWalletRechargeTable : Table<PayWalletRecharge, Long> by PayWalletRechargeTableImpl
