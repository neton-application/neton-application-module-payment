package table

import model.WalletWithdrawAuditLog
import model.WalletWithdrawAuditLogTableImpl
import neton.database.api.Table

object WalletWithdrawAuditLogTable : Table<WalletWithdrawAuditLog, Long> by WalletWithdrawAuditLogTableImpl
