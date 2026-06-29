package init

import infra.TableRegistryBuilder
import neton.core.component.NetonContext
import neton.core.config.getEnv
import neton.logging.LoggerFactory
import neton.security.internal.HmacSha256
import logic.UserBankCardLogic
import logic.crypto.BankCardCrypto
import logic.crypto.EnvWalletCryptoKeyProvider
import model.*
import table.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// MANIFEST-P3: 手写 runtime bootstrap。7 个 PayXxxLogic 已标 @Logic →
// 生成的 PaymentLogicInitializer 装配; moduleId/dependsOn/migrations/路由 由
// KSP manifest 持有。这里负责 Table registry 注册 + 银行卡加密装配（非 @Logic 机制）。
object PaymentRuntimeBootstrap {
    @OptIn(ExperimentalEncodingApi::class)
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
        registry.register(UserBankCard::class, UserBankCardTable)
        registry.register(WalletWithdrawOrder::class, WalletWithdrawOrderTable)
        registry.register(WalletWithdrawAuditLog::class, WalletWithdrawAuditLogTable)

        // 银行卡卡号信封加密（P4-B1）：env 主密钥 → BankCardCrypto → 注入 UserBankCardLogic。
        // 手动 ctx.bind 早于生成的 PaymentLogicInitializer（absent-才-bind，不会被覆盖）；
        // UserBankCardLogic 非 @Logic，因为它要注入 crypto，而生成器只会注 log。
        // 第一版 env 主密钥；生产可换 KMS provider（WalletCryptoKeyProvider 抽象已就位）。
        val log = ctx.get(LoggerFactory::class).get("logic.user-bank-card")
        val masterKeyB64 = getEnv(EnvWalletCryptoKeyProvider.ENV_KEY_NAME)
        val crypto: BankCardCrypto? = if (masterKeyB64.isNullOrBlank()) {
            log.warn(
                "bank-card.crypto.disabled",
                mapOf("reason" to "env ${EnvWalletCryptoKeyProvider.ENV_KEY_NAME} not set"),
            )
            null
        } else {
            val keyProvider = EnvWalletCryptoKeyProvider(masterKeyB64)
            // 用主密钥派生独立的 HMAC key（域分隔，避免与加密 key 同值）。
            val hmacKey = HmacSha256.signForPassword(
                Base64.decode(masterKeyB64),
                "neton-bank-card-hash-v1".encodeToByteArray(),
            )
            BankCardCrypto(keyProvider, hmacKey)
        }
        ctx.bind(UserBankCardLogic::class, UserBankCardLogic(log = log, crypto = crypto))
    }
}
