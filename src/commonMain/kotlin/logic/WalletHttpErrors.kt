package logic

import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.core.http.ValidationError

/**
 * 支付 / 钱包业务拒绝 → 标准 4xx（P4-F error mapping）。
 * 框架按 [NetonErrorCode] 推导 HTTP status：INVALID_PARAMS→400、RESOURCE_NOT_FOUND→404、
 * OPERATION_CONFLICT→409、PERMISSION_DENIED→403。业务拒绝绝不能落 500。
 *
 * [code] 必须是 [PaymentErrorCodes] 里的机器码；[field] 给出时附一条字段错误
 * `ValidationError(field, code, code)`，供表单逐项提示。
 */

/** 参数/金额非法 → 400 */
internal fun walletBadRequest(code: String, field: String? = null): Nothing =
    throw HttpException(NetonErrorCode.INVALID_PARAMS, code, fieldErrors(code, field))

/** 订单/银行卡/钱包不存在 → 404 */
internal fun walletNotFound(code: String): Nothing =
    throw HttpException(NetonErrorCode.RESOURCE_NOT_FOUND, code)

/** 状态不允许 / 重复操作 / 乐观锁冲突 / 余额（可用）不足 → 409 */
internal fun walletConflict(code: String): Nothing =
    throw HttpException(NetonErrorCode.OPERATION_CONFLICT, code)

/** 资金授权失败（非会话成员领取他人红包等，#84）→ 403 */
internal fun walletForbidden(code: String): Nothing =
    throw HttpException(NetonErrorCode.PERMISSION_DENIED, code)

/** 参数守卫：失败 → 400（替代 require，保持同一判定条件）。 */
internal fun requireParam(value: Boolean, code: String, field: String? = null) {
    if (!value) walletBadRequest(code, field)
}

/** 状态守卫：失败 → 409（替代状态机 require，保持同一判定条件）。 */
internal fun requireState(value: Boolean, code: String) {
    if (!value) walletConflict(code)
}

private fun fieldErrors(code: String, field: String?): List<ValidationError> =
    if (field == null) emptyList() else listOf(ValidationError(field, code, code))
