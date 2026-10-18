package logic

import contract.testing.ErrorMessageGuard
import kotlin.test.Test

/** payment 模块的业务错误 message 必须是 [PaymentErrorCodes] 里的机器码。 */
class ErrorMessageGuardTest {
    @Test
    fun businessErrorMessagesAreMachineCodes() {
        ErrorMessageGuard.assertModuleClean(
            allowlist = setOf(
                // 统一出口：code 形参只接收 PaymentErrorCodes 常量；调用点由下面的 constructors 逐个检查。
                "kotlin/logic/WalletHttpErrors.kt#HttpException#code",
                "kotlin/logic/WalletHttpErrors.kt#ValidationError#code",
                "kotlin/logic/WalletHttpErrors.kt#walletBadRequest#code",
                "kotlin/logic/WalletHttpErrors.kt#walletConflict#code",
            ),
            // 模块自己的抛错 helper：message 位置的实参必须是机器码常量。
            constructors = ErrorMessageGuard.DEFAULT_CONSTRUCTORS + mapOf(
                "walletBadRequest" to listOf(0),
                "walletNotFound" to listOf(0),
                "walletConflict" to listOf(0),
                "walletForbidden" to listOf(0),
                "requireParam" to listOf(1),
                "requireState" to listOf(1),
            ),
        )
    }
}
