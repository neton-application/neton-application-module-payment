package init

import neton.core.component.NetonContext
import neton.core.config.getEnv
import neton.logging.LoggerFactory
import neton.security.crypto.HmacSha256
import logic.PayOrderLogic
import logic.UserBankCardLogic
import logic.crypto.BankCardCrypto
import logic.crypto.EnvWalletCryptoKeyProvider
import channel.PayChannelRegistry
import port.PayOrderPaidPort
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// MANIFEST-P3: 手写 runtime bootstrap。PayXxxLogic 中 @Logic 的由生成的
// PaymentLogicInitializer 装配; moduleId/dependsOn/migrations/路由 由 KSP manifest。
// 这里负责需要额外依赖注入的 logic：银行卡加密、支付订单（渠道 registry + 可选 paidPort）。
object PaymentRuntimeBootstrap {
    @OptIn(ExperimentalEncodingApi::class)
    fun initialize(ctx: NetonContext) {
        // 支付订单：渠道 registry（默认全 mock）+ 可选 PayOrderPaidPort（业务方 application 层 bind）。
        // 非 @Logic，因为要注入 registry/paidPort，生成器只会注 log。
        val orderLog = ctx.get(LoggerFactory::class).get("logic.pay-order")
        val registry = ctx.getOrNull(PayChannelRegistry::class) ?: PayChannelRegistry.defaultMock()
        val paidPort = ctx.getOrNull(PayOrderPaidPort::class)
        ctx.bind(PayOrderLogic::class, PayOrderLogic(log = orderLog, channels = registry, paidPort = paidPort))

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
            val hmacKey = HmacSha256.sign(
                Base64.decode(masterKeyB64),
                "neton-bank-card-hash-v1".encodeToByteArray(),
            )
            BankCardCrypto(keyProvider, hmacKey)
        }
        ctx.bind(UserBankCardLogic::class, UserBankCardLogic(log = log, crypto = crypto))
    }
}
