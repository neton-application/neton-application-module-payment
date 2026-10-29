package logic

import controller.admin.channel.dto.CreatePayChannelRequest
import controller.admin.channel.dto.UpdatePayChannelRequest
import dto.PageResponse
import model.PayChannel
import table.PayChannelTable
import neton.core.http.ValidationError
import neton.core.http.NetonErrorCode
import neton.core.http.HttpException
import neton.core.http.NotFoundException
import neton.database.dsl.*

import neton.logging.Logger

@neton.core.annotations.Logic(logger = "logic.pay-channel")
class PayChannelLogic(
    private val log: Logger
) {

    suspend fun create(request: CreatePayChannelRequest): Long {
        // 编码是业务唯一键（uk_pay_channels_code）。不先查一下的话，重复编码会撞唯一约束，
        // 而 sqlx4k 的 SQLError 被兜底成 `500 Internal Server Error` —— 运营看到的是「后台坏了」，
        // 而不是「这个编码已经有了」。同一处置见 QuotaLogic.validateGrant 的注释。
        getByCode(request.code)?.let {
            throw HttpException(
                NetonErrorCode.RESOURCE_ALREADY_EXISTS,
                PaymentErrorCodes.PAY_CHANNEL_CODE_EXISTS,
                listOf(ValidationError("code", PaymentErrorCodes.PAY_CHANNEL_CODE_EXISTS, PaymentErrorCodes.PAY_CHANNEL_CODE_EXISTS)),
            )
        }
        return PayChannelTable.insert(
            PayChannel(
                code = request.code,
                platformCode = request.platformCode,
                method = request.method,
                platformChannelId = request.platformChannelId,
                displayMode = request.displayMode,
                config = PayChannelSecrets.merge(request.config, stored = null),
                status = request.status,
                feeRate = request.feeRate,
                remark = request.remark
            )
        ).id
    }

    suspend fun update(request: UpdatePayChannelRequest) {
        val current = PayChannelTable.get(request.id)
            ?: throw NotFoundException(PaymentErrorCodes.PAY_CHANNEL_NOT_FOUND)
        // 在原记录上改：整行写回时 created_at 不能丢（曾因此每次编辑都 500）
        PayChannelTable.update(
            current.copy(
                code = request.code,
                platformCode = request.platformCode,
                method = request.method,
                platformChannelId = request.platformChannelId,
                displayMode = request.displayMode,
                // 后台读到的密钥是遮罩：原样提交回来 / 留空 / 不写都沿用库里的值
                config = PayChannelSecrets.merge(request.config, stored = current.config),
                status = request.status,
                feeRate = request.feeRate,
                remark = request.remark,
            )
        )
    }

    suspend fun delete(id: Long) {
        PayChannelTable.get(id)
            ?: throw NotFoundException(PaymentErrorCodes.PAY_CHANNEL_NOT_FOUND)
        PayChannelTable.destroy(id)
    }

    /** 后台读接口返回的通道：配置里的密钥换成遮罩。 */
    fun forAdmin(channel: PayChannel): PayChannel = channel.copy(config = PayChannelSecrets.mask(channel.config))

    /** 渠道按 code 全局唯一 —— 单应用部署不需要再按 app 维度分组。 */
    suspend fun getByCode(code: String): PayChannel? =
        PayChannelTable.oneWhere { PayChannel::code eq code }

    suspend fun getEnableCodeList(): List<String> =
        PayChannelTable.query {
            where { PayChannel::status eq 1 }
        }.list().map { it.code }

    suspend fun page(
        page: Int,
        size: Int,
        code: String? = null,
        status: Int? = null
    ): PageResponse<PayChannel> {
        val result = PayChannelTable.query {
            where {
                and(
                    whenNotBlank(code) { PayChannel::code eq it },
                    whenPresent(status) { PayChannel::status eq it }
                )
            }
            orderBy(PayChannel::id.desc())
        }.page(page, size)
        // 后台列表的配置里密钥只给遮罩（见 PayChannelSecrets）
        return PageResponse(result.items.map(::forAdmin), result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }
}
