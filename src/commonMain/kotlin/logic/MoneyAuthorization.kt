package logic

/**
 * 资金授权层（GA blocker #84）。
 *
 * **Authentication ≠ Authorization。** JWT sub 只回答"你是谁"(已登录);资金能力还必须回答
 * "你有没有资格"(是否属于这个会话 / DM 双方 / 群成员 / 目标接收方)。此前红包 `claim` 只用
 * JWT sub 定身份、不校验会话归属 → 任何知道 redPacketId 的用户都能领走别人的红包(实测 P0)。
 *
 * **分层约束**：payment 是 canonical 通用模块,不知道聊天会话/成员(那是 module-privchat 产品层
 * 概念),因此授权判定由产品层在启动时装配实现(基于 IM 会话成员关系)。未装配(`provider == null`,
 * 如非 IM 产品部署或纯支付网关)时按"通用支付无会话概念"放行 —— 与 [MoneyMessageCardInjector]
 * 的装配约定一致。
 *
 * **统一入口**：所有资金能力的授权都经此,不在各 API/logic 内散落 `if`。未来 canReceiveTransfer /
 * canOpenWalletCard / canViewWalletOrder 等按需扩展到本接口,由同一产品层实现集中提供。
 */
interface MoneyAuthorizationProvider {
    /**
     * 领取者 [userId] 是否有资格领取该红包。
     * @param channelId    红包所在会话
     * @param scene        0=DM(仅会话双方可领) / 1=群(仅群成员可领)
     * @param senderUserId 红包发送方
     */
    suspend fun canClaimRedPacket(
        userId: Long,
        channelId: String,
        scene: Int,
        senderUserId: Long,
    ): Boolean
}

/** 装配点：产品层(module-privchat bootstrap)启动时赋值；payment 侧只读。 */
object MoneyAuthorization {
    @kotlin.concurrent.Volatile
    var provider: MoneyAuthorizationProvider? = null

    /**
     * 领取授权闸。provider 未装配(非 IM 部署)→ 放行(通用支付无会话概念);
     * 装配则严格按会话成员关系判定,拒绝抛 403。
     */
    suspend fun requireCanClaimRedPacket(
        userId: Long,
        channelId: String,
        scene: Int,
        senderUserId: Long,
    ) {
        val p = provider ?: return
        val ok = p.canClaimRedPacket(userId, channelId, scene, senderUserId)
        if (!ok) walletForbidden("not authorized to claim this red packet")
    }
}
