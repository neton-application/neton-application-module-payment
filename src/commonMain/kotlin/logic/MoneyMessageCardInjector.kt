package logic

/**
 * RP-12 资金卡片**同步注入主路径**（对标微信：资金成功 = 订单 + ledger + IM 卡片三者都成功）。
 *
 * RedPacketLogic.send / MoneyTransferLogic.transfer 在资金事务内调用：注入成功返回 server
 * message_id（同事务把 outbox 行标 SENT，job 只作兜底补偿）；**注入失败抛异常 → 整个资金事务
 * 回滚**（不扣款、不建单），客户端收到「发送失败」。dedupKey 幂等保证超时重试不出重复卡片。
 *
 * 分层约束：payment 是 canonical 通用模块，不能依赖 module-privchat —— 由产品层（module-privchat
 * bootstrap）在启动时装配实现；未装配（null，如非 IM 产品部署）退回纯 outbox 异步注入（原行为）。
 */
interface MoneyMessageCardInjector {
    /**
     * 同步注入一张资金卡片消息。
     * @param eventType RED_PACKET_CARD / MONEY_TRANSFER_CARD
     * @param refType   RED_PACKET / MONEY_TRANSFER（订单类型，dedupKey 用）
     * @param refId     订单 id
     * @return 注入成功的 server message_id
     * @throws Exception 注入失败（调用方应让资金事务回滚）
     */
    suspend fun injectCard(
        eventType: String,
        refType: String,
        refId: Long,
        channelId: String,
        senderUserId: Long,
        payloadJson: String,
    ): Long
}

/** 装配点：产品层启动时赋值；payment 侧只读。 */
object MoneyMessageInjection {
    @kotlin.concurrent.Volatile
    var injector: MoneyMessageCardInjector? = null
}
