package logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 商户订单号的前缀路由。
 *
 * 这是多消费方共用一条事件的分流依据：认错了会把别人的订单当成自己的处理
 * （给内容购买的订单去加钱包余额），认漏了则用户付了钱不到账。
 * 两种都是资损，且都不会抛异常。
 */
class WalletRechargePaidListenerTest {

    @Test
    fun parsesOwnPrefix() {
        assertEquals(42L, WalletRechargePaidListener.parse("wallet:recharge:42"))
    }

    @Test
    fun roundTripsWithBuilder() {
        val mid = WalletRechargePaidListener.merchantOrderId(7)
        assertEquals(7L, WalletRechargePaidListener.parse(mid))
    }

    /** 其它业务的订单必须返回 null —— 由它们各自的监听者处理。 */
    @Test
    fun ignoresOtherBusinessPrefixes() {
        assertNull(WalletRechargePaidListener.parse("content:item:1:2"))
        assertNull(WalletRechargePaidListener.parse("content:vip:1:2"))
        assertNull(WalletRechargePaidListener.parse("order-123"))
        assertNull(WalletRechargePaidListener.parse(""))
    }

    /** 前缀对但 id 不是数字：宁可跳过也不能猜，否则会去操作一个不存在的充值单。 */
    @Test
    fun rejectsMalformedId() {
        assertNull(WalletRechargePaidListener.parse("wallet:recharge:abc"))
        assertNull(WalletRechargePaidListener.parse("wallet:recharge:"))
    }
}
