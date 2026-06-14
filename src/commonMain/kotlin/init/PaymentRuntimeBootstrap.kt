package init

import infra.TableRegistryBuilder
import neton.core.component.NetonContext
import model.*
import table.*

// MANIFEST-P3: 手写 runtime bootstrap。7 个 PayXxxLogic 已标 @Logic →
// 生成的 PaymentLogicInitializer 装配; moduleId/dependsOn/migrations/路由 由
// KSP manifest 持有。这里只剩 Table registry 注册 (非 @Logic 机制)。
object PaymentRuntimeBootstrap {
    fun initialize(ctx: NetonContext) {
        val registry = ctx.get(TableRegistryBuilder::class)
        registry.register(PayApp::class, PayAppTable)
        registry.register(PayChannel::class, PayChannelTable)
        registry.register(PayOrder::class, PayOrderTable)
        registry.register(PayRefund::class, PayRefundTable)
        registry.register(PayNotifyTask::class, PayNotifyTaskTable)
        registry.register(PayWallet::class, PayWalletTable)
        registry.register(PayWalletRecharge::class, PayWalletRechargeTable)
        registry.register(PayWalletTransaction::class, PayWalletTransactionTable)
        registry.register(PayWalletRechargePackage::class, PayWalletRechargePackageTable)
        registry.register(PayTransfer::class, PayTransferTable)
    }
}
