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

internal fun WalletFreezeLogic.VisibleFreeze.toVO(): WalletFreezeVO = WalletFreezeVO(
    id = freeze.id,
    freezeType = freeze.freezeType,
    amount = shownAmount,
    status = freeze.status,
    // 只下发**运营真填的**单据号。留空时服务端造的那串幂等键（auto: 开头）不是单据，
    // 摆到用户面前就是一行没人看得懂的乱码。
    refId = freeze.refId.takeIf { WalletFreezeLogic.isRealDocument(it) },
    reasonText = freeze.reasonText?.takeIf { it.isNotBlank() },
    createdAt = freeze.createdAt,
    releasedAt = freeze.releasedAt,
)
