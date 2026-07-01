package logic

import neton.core.http.HttpContext
import neton.core.interfaces.Identity

/**
 * 不可抵赖操作上下文（P0）：who + 请求来源。控制器用 [from] 从 Identity + HttpContext 构造，
 * 传入 logic 后写进审计（提现审计 / 敏感操作审计）。
 */
data class OperatorContext(
    val operatorId: Long,
    val operatorName: String? = null,
    val operatorRole: String? = null,
    val ip: String? = null,
    val userAgent: String? = null,
    val traceId: String? = null,
) {
    companion object {
        /** 系统 / 测试用：仅 operatorId，无来源上下文。 */
        fun of(operatorId: Long): OperatorContext = OperatorContext(operatorId)

        /**
         * 从请求上下文构造。operatorRole 取 identity.roles；operatorName 暂无来源
         * （Identity 只暴露 id/roles/permissions），先留 null，后续接入用户名再补。
         */
        fun from(identity: Identity, ctx: HttpContext): OperatorContext = OperatorContext(
            operatorId = identity.id.toLong(),
            operatorRole = identity.roles.joinToString(",").ifBlank { null },
            ip = ctx.request.remoteAddress.ifBlank { null },
            userAgent = ctx.request.userAgent,
            traceId = ctx.traceId.ifBlank { null },
        )
    }
}
