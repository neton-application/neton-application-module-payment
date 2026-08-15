package init

import neton.core.component.NetonContext
import neton.core.config.getEnv
import neton.logging.LoggerFactory
import neton.security.crypto.HmacSha256
import logic.PayOrderLogic
import logic.UserBankCardLogic
import logic.crypto.BankCardCrypto
import logic.crypto.EnvWalletCryptoKeyProvider
import channel.PayPlatformRegistry
import neton.core.event.DomainEventBus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// MANIFEST-P3: 手写 runtime bootstrap。PayXxxLogic 中 @Logic 的由生成的
// PaymentLogicInitializer 装配; moduleId/dependsOn/migrations/路由 由 KSP manifest。
// 这里负责需要额外依赖注入的 logic：银行卡加密、支付订单（渠道 registry + 可选 paidPort）。
object PaymentRuntimeBootstrap {
    @OptIn(ExperimentalEncodingApi::class)
    fun initialize(ctx: NetonContext) {
        // 支付订单：渠道 registry（默认全 mock）+ 可选事件总线（装配层收集各模块监听者后 bind）。
        // 非 @Logic，因为要注入 registry/events，生成器只会注 log。
        // 在线充值到账：支付成功 → 充值单已支付 → 余额入账。
        // 在此注册而不是装配层，是因为它依赖 @Logic 装配的 PayWalletLogic，
        // 而装配层的 bind 跑在模块初始化之前，那时它还不存在。
        ctx.get(neton.core.event.DomainEventBus::class).register(
            logic.WalletRechargePaidListener(ctx.get(logic.PayWalletLogic::class))
        )

        // 本模块的全局设置定义登记进注册表，后台可见可改。
        ctx.getOrNull(setting.SettingDefinitionRegistry::class)
            ?.register(setting.PaymentSettingKeys.definitions)

        val orderLog = ctx.get(LoggerFactory::class).get("logic.pay-order")
        val registry = ctx.getOrNull(PayPlatformRegistry::class) ?: PayPlatformRegistry.default()
        // 总线由框架在启动最早期绑定，这里必然取得到。用 get 而不是 getOrNull：
        // 后者配上 PayOrderLogic 里的 `events?.publish(...)`，漏装配时是整条链路静默空转
        // （在线充值不自动到账，且没有任何信号），正是之前发生过的事。
        ctx.bind(
            PayOrderLogic::class,
            PayOrderLogic(log = orderLog, platforms = registry, events = ctx.get(DomainEventBus::class)),
        )

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
