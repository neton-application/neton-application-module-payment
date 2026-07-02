package controller.admin.wallet.dto

import kotlinx.serialization.Serializable

/**
 * 财务总览轻量版（P1，通用 payment 能力）。金额单位=分。
 *
 * 钱包资金聚合 + 提现分状态聚合，供后台财务中心首屏。聚合走 DB SUM/COUNT（不 list 全表）。
 */
@Serializable
data class WalletOverviewVO(
    val walletCount: Long = 0,
    val totalBalance: Long = 0,        // sum(balance)
    val totalFrozen: Long = 0,         // sum(freeze_price)
    val totalAvailable: Long = 0,      // totalBalance - totalFrozen
    val totalRecharge: Long = 0,       // sum(total_recharge)
    val totalExpense: Long = 0,        // sum(total_expense)
    val withdrawPendingCount: Long = 0,
    val withdrawPendingAmount: Long = 0,
    val withdrawApprovedCount: Long = 0,
    val withdrawApprovedAmount: Long = 0,
    val withdrawPaidCount: Long = 0,
    val withdrawPaidAmount: Long = 0,
)
