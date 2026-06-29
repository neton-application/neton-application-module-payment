package table

import model.UserBankCard
import model.UserBankCardTableImpl
import neton.database.api.Table

object UserBankCardTable : Table<UserBankCard, Long> by UserBankCardTableImpl
