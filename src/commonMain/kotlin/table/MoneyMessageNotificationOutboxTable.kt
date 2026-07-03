package table

import model.MoneyMessageNotificationOutbox
import model.MoneyMessageNotificationOutboxTableImpl
import neton.database.api.Table

object MoneyMessageNotificationOutboxTable :
    Table<MoneyMessageNotificationOutbox, Long> by MoneyMessageNotificationOutboxTableImpl
