package table

import model.PaySensitiveAuditLog
import model.PaySensitiveAuditLogTableImpl
import neton.database.api.Table

object PaySensitiveAuditLogTable : Table<PaySensitiveAuditLog, Long> by PaySensitiveAuditLogTableImpl
