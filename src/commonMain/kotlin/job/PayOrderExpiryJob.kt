package job

import logic.PayOrderLogic
import neton.core.component.NetonContext
import neton.jobs.Job
import neton.jobs.JobContext
import neton.jobs.JobExecutor
import neton.logging.Logger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 关闭过期未支付订单（每 5 分钟）。
 *
 * 支付渠道那边超时不会主动告诉我们，只能自己扫。不扫的后果是订单永远停在
 * "待支付"：占着 merchantOrderId 让业务方重新下单撞唯一键，后台的待支付统计也是假的。
 *
 * SINGLE_NODE：多实例部署时只让一个节点扫，避免同一批订单被并发关闭
 * （逻辑本身有 status 复查兜底，但重复扫只是白费数据库）。
 */
@OptIn(ExperimentalTime::class)
@Job(id = "pay-order-expiry", cron = "*/5 * * * *", lockTtlMs = 300_000)
class PayOrderExpiryJob(
    private val log: Logger,
    private val ctx: NetonContext,
) : JobExecutor {

    override suspend fun run(context: JobContext) {
        // PayOrderLogic 由 PaymentRuntimeBootstrap 手动 bind（要注入渠道 registry 与事件总线），
        // 所以从 ctx 取而不是构造参数直接注入。
        val orders = ctx.getOrNull(PayOrderLogic::class) ?: run {
            log.warn("pay.order.expiry.skipped reason=PayOrderLogic_not_bound")
            return
        }
        val closed = orders.closeExpired(Clock.System.now().toEpochMilliseconds())
        if (closed > 0) log.info("pay.order.expiry closed=$closed")
    }
}
