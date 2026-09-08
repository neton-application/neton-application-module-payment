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
 * 本任务的 @Job id。提成常量是因为装配层要按这个 id 查它的运行期启用状态
 * （启动时检查「回调基址没配 + 本任务被停用」这个组合 —— 两条到账路径同时断掉，
 * 钱会到账而额度永远不发）。两边各写一份字面量的话，改 id 时漏掉的那一处
 * 表现为「启动检查永远查不到这个任务」，而那正好是它本该响的时候不响。
 */
const val PAY_RECONCILE_JOB_ID = "pay-order-reconcile"

/**
 * 支付对账：拿待支付订单去渠道主动查一次，把丢失的回调补回来。
 *
 * 回调**一定会丢** —— 渠道重试次数有限，而我们可能正好在重启、或网络抖了一下。
 * 没有这一步，丢掉的那笔就只能等用户投诉，而用户投诉的是"钱扣了没到账"，
 * 这类问题的处理成本远高于多跑一个定时任务。
 *
 * 只查询、不臆断：渠道说成功才推进，说别的就保持原样等下一轮或等超时关单。
 * SINGLE_NODE 是因为它会对每笔待支付订单发一次外部请求，多节点同时跑纯属浪费额度。
 */
@OptIn(ExperimentalTime::class)
@Job(id = PAY_RECONCILE_JOB_ID, cron = "*/10 * * * *", lockTtlMs = 600_000)
class PayOrderReconcileJob(
    private val log: Logger,
    private val ctx: NetonContext,
) : JobExecutor {

    override suspend fun run(context: JobContext) {
        // PayOrderLogic 由 PaymentRuntimeBootstrap 手动 bind（需注入渠道注册表与事件总线）
        val orders = ctx.getOrNull(PayOrderLogic::class) ?: return
        val recovered = orders.reconcileWaiting(Clock.System.now().toEpochMilliseconds())
        if (recovered > 0) log.info("pay.order.reconcile recovered=$recovered")
    }
}
