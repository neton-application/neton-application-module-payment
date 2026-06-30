package logic

import neton.core.http.HttpException
import neton.core.http.NetonErrorCode

/**
 * 提现/银行卡业务拒绝 → 标准 4xx（P4-F error mapping）。
 * 框架按 [NetonErrorCode] 推导 HTTP status：INVALID_PARAMS→400、RESOURCE_NOT_FOUND→404、
 * OPERATION_CONFLICT→409、PERMISSION_DENIED→403。业务拒绝绝不能落 500。
 *
 * 仅改异常类型，不改任何判定逻辑（守卫条件原样保留）。无权限由 @Permission 框架层处理（403）。
 */

/** 参数/金额非法 → 400 */
internal fun walletBadRequest(message: String): Nothing =
    throw HttpException(NetonErrorCode.INVALID_PARAMS, message)

/** 订单/银行卡/钱包不存在 → 404 */
internal fun walletNotFound(message: String): Nothing =
    throw HttpException(NetonErrorCode.RESOURCE_NOT_FOUND, message)

/** 状态不允许 / 重复操作 / 乐观锁冲突 / 余额（可用）不足 → 409 */
internal fun walletConflict(message: String): Nothing =
    throw HttpException(NetonErrorCode.OPERATION_CONFLICT, message)

/** 参数守卫：失败 → 400（替代 require，保持同一判定条件）。 */
internal inline fun requireParam(value: Boolean, lazyMessage: () -> String) {
    if (!value) walletBadRequest(lazyMessage())
}

/** 状态守卫：失败 → 409（替代状态机 require，保持同一判定条件）。 */
internal inline fun requireState(value: Boolean, lazyMessage: () -> String) {
    if (!value) walletConflict(lazyMessage())
}
