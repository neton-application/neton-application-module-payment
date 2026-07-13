package logic

/**
 * #93-A2 **test-only** 交付故障注入。仅供跨端故障回归（scripts/e2e/.../rc93a2-*）验证这条恢复链：
 * 快投递失败 → 用户侧看到 `PROCESSING` → 后台 worker 补发 → `DELIVERED`。
 *
 * 安全边界（GPT 红线：故障注入只能 test/dev 开启，生产必须 fail-closed）：
 *   - **默认 OFF**。只能由**启动期 env** 开启，**无任何 HTTP/admin 开关** —— 不可在运行期被任意打开。
 *   - **fail-closed**：开启 `MONEY_DELIVERY_TEST_FAIL_FAST=1` 但未显式声明测试环境
 *     `MONEY_DELIVERY_TEST_MODE=1` → [configure] 直接 `error()`，进程拒绝启动（未声明测试环境即视作生产）。
 *
 * 语义：[failFast]=true 时 `MoneyMessageDelivery.tryDeliverByRef` 的**快投递路径不注入**、原样返回
 * `DeliveryOutcome.Processing`，outbox 行留 PENDING；后台 worker（`drainPending`，**不读此标记**）随后
 * 正常补发。资金真相在 payment 事务内已成立，交付是可重试副作用 —— 故障注入**不影响资金一致性**。
 */
object MoneyMessageDeliveryFault {
    @kotlin.concurrent.Volatile
    var failFast: Boolean = false
        private set

    /**
     * 启动期一次性装配（bootstrap 调用）。[enabled] 来自 `MONEY_DELIVERY_TEST_FAIL_FAST=1`；
     * [testModeConfirmed] 来自 `MONEY_DELIVERY_TEST_MODE=1`。enabled 但未确认测试环境 → fail-closed。
     */
    fun configure(enabled: Boolean, testModeConfirmed: Boolean) {
        if (enabled && !testModeConfirmed) {
            error(
                "MONEY_DELIVERY_TEST_FAIL_FAST=1 是 test-only 交付故障注入，必须同时显式声明 " +
                    "MONEY_DELIVERY_TEST_MODE=1 才允许启动（fail-closed：未声明测试环境即视作生产环境，" +
                    "拒绝带故障注入启动）。生产部署绝不可设置这两个环境变量。",
            )
        }
        failFast = enabled
    }
}
