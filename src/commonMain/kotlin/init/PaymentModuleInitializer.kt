package init

import infra.TableRegistryBuilder
import neton.core.component.NetonContext
import neton.core.module.ModuleInitializer
import neton.logging.LoggerFactory

import model.*
import table.*
import logic.*

object PaymentModuleInitializer : ModuleInitializer {

    override val moduleId: String = "payment"
    override val dependsOn: List<String> = listOf("system")

    override fun initialize(ctx: NetonContext) {
        val loggerFactory = ctx.get(LoggerFactory::class)
        val registry = ctx.get(TableRegistryBuilder::class)

        // 注册 Table
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

        // 绑定 Logic
        ctx.bind(PayAppLogic::class, PayAppLogic(loggerFactory.get("logic.pay-app")))
        ctx.bind(PayOrderLogic::class, PayOrderLogic(loggerFactory.get("logic.pay-order")))
        ctx.bind(PayRefundLogic::class, PayRefundLogic(loggerFactory.get("logic.pay-refund")))
        ctx.bind(PayWalletLogic::class, PayWalletLogic(loggerFactory.get("logic.pay-wallet")))
        ctx.bind(PayNotifyLogic::class, PayNotifyLogic(loggerFactory.get("logic.pay-notify")))

        // 注册 KSP 生成的路由
        neton.module.payment.generated.PaymentRouteInitializer.initialize(ctx)
    }
}
