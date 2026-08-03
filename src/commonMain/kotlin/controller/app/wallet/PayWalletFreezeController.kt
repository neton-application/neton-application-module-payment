package controller.app.wallet

import controller.app.wallet.dto.WalletFreezePageVO
import controller.app.wallet.dto.WalletFreezeVO
import logic.PayWalletLogic
import logic.WalletFreezeLogic
import logic.WalletFreezeType
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.interfaces.Identity

/**
 * 我的冻结（用户端）。钱包页「冻结中 ¥X」点进来就是这里。
 *
 * 只读。冻结的下达/解除全在后台，用户端没有任何写入口。
 */
@Controller("/pay/wallet-freeze")
class PayWalletFreezeController(
    private val payWalletLogic: PayWalletLogic,
    private val freezeLogic: WalletFreezeLogic,
) {

    @Get("/page")
    suspend fun page(
        identity: Identity,
        pageNo: Int = 1,
        pageSize: Int = 20,
    ): WalletFreezePageVO {
        val wallet = payWalletLogic.getWallet(identity.id.toLong()) ?: return WalletFreezePageVO()
        val (items, total) = freezeLogic.pageMyFreezes(wallet.id, pageNo, pageSize)
        val size = pageSize.coerceAtLeast(1)
        return WalletFreezePageVO(
            list = items.map { it.toVO() },
            total = total,
            page = pageNo,
            size = pageSize,
            totalPages = ((total + size - 1) / size).toInt(),
        )
    }

    @Get("/detail")
    suspend fun detail(identity: Identity, id: Long): WalletFreezeVO? {
        val wallet = payWalletLogic.getWallet(identity.id.toLong()) ?: return null
        return freezeLogic.myFreezeDetail(wallet.id, id)?.toVO()
    }
}

private fun WalletFreezeLogic.VisibleFreeze.toVO(): WalletFreezeVO = WalletFreezeVO(
    id = freeze.id,
    freezeType = freeze.freezeType,
    amount = shownAmount,
    status = freeze.status,
    // 账户冻结（司法）不下发任何关联信息：那是法律文书号。
    refId = freeze.refId.takeIf { freeze.freezeType != WalletFreezeType.JUDICIAL },
    createdAt = freeze.createdAt,
    releasedAt = freeze.releasedAt,
)
