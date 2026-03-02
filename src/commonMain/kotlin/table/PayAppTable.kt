package table

import model.PayApp
import model.PayAppTableImpl
import neton.database.api.Table

object PayAppTable : Table<PayApp, Long> by PayAppTableImpl
