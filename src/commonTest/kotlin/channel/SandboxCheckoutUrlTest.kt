package channel

import kotlinx.coroutines.runBlocking
import model.PayChannel
import model.PayOrder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 沙箱收银台的地址：控制台与后端常常不在一个域名下（控制台 neton.cloud，后端经 api.neton.cloud），
 * 相对地址在控制台页面里打开就是控制台自己的路由，打不开收银台。配了回调基址就给绝对地址。
 */
class SandboxCheckoutUrlTest {

    private val order = PayOrder(merchantOrderId = "m-1", subject = "额度充值", price = 100)
    private val channel = PayChannel(code = "sandbox_alipay", platformCode = SandboxPayPlatform.PLATFORM_CODE, config = "{}")

    @Test fun absoluteWhenTheCallbackBaseIsKnown() = runBlocking {
        val r = SandboxPayPlatform(payNotifyUrlOf("https://api.example.com/")).prepay(order, channel)
        assertEquals("https://api.example.com/app/pay/mock/checkout?merchantOrderId=m-1&channelCode=sandbox_alipay", r.payload)
    }

    @Test fun relativeWithoutACallbackBase() = runBlocking {
        assertEquals("/app/pay/mock/checkout?merchantOrderId=m-1&channelCode=sandbox_alipay", SandboxPayPlatform().prepay(order, channel).payload)
        assertEquals("/app/pay/mock/checkout?merchantOrderId=m-1&channelCode=sandbox_alipay", SandboxPayPlatform(payNotifyUrlOf("")).prepay(order, channel).payload)
    }
}
