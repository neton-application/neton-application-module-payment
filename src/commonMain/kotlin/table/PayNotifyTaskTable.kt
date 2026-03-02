package table

import model.PayNotifyTask
import model.PayNotifyTaskTableImpl
import neton.database.api.Table

object PayNotifyTaskTable : Table<PayNotifyTask, Long> by PayNotifyTaskTableImpl
