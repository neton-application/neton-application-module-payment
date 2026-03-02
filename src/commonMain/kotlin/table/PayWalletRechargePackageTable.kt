package table

import model.PayWalletRechargePackage
import model.PayWalletRechargePackageTableImpl
import neton.database.api.Table

object PayWalletRechargePackageTable : Table<PayWalletRechargePackage, Long> by PayWalletRechargePackageTableImpl
