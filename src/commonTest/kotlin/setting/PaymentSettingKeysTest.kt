package setting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 模拟支付开关的默认值与解析。
 *
 * 看着琐碎，守的却是资损：这个开关一旦默认打开、或把无法识别的值解析成 true，
 * 部署就会在无人察觉的情况下允许「点一下即支付成功」。
 * 真正的兜底是它与真实渠道的互斥（见 PayOrderLogic.resolveClient），
 * 但第一道门必须先关严。
 */
class PaymentSettingKeysTest {

    private val key = PaymentSettingKeys.MOCK_PAY_ENABLED

    @Test
    fun disabledByDefault() {
        assertFalse(key.default, "模拟支付默认必须关闭")
    }

    @Test
    fun parsesExplicitValues() {
        assertEquals(true, key.parse("true"))
        assertEquals(true, key.parse("1"))
        assertEquals(false, key.parse("false"))
        assertEquals(false, key.parse("0"))
    }

    /** 无法识别的值必须解析失败，由读取方退回默认（关闭），而不是被当成开启。 */
    @Test
    fun rejectsUnrecognisedValues() {
        assertEquals(null, key.parse("enabled"))
        assertEquals(null, key.parse(""))
        assertEquals(null, key.parse("是"))
    }

    @Test
    fun isRegisteredForAdminVisibility() {
        assertTrue(PaymentSettingKeys.definitions.contains(key), "未登记则后台看不到，也就无从关闭")
        assertEquals("payment", key.category)
    }
}
