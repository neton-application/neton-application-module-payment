package setting

import model.SystemSetting
import neton.database.dsl.*
import table.SystemSettingTable

/**
 * payment 模块的全局设置（后台可见可改，改完立即生效）。
 *
 * 与 config 目录下 .conf 的部署期配置刻意区分：后者改了要重启，属于"这台机器怎么跑"；
 * 这里是"业务怎么运作"，运营在后台就能调。
 */
object PaymentSettingKeys {

    const val CATEGORY = "payment"

    /**
     * 模拟支付模式。
     *
     * 打开后模拟收银台与 `mock-success` 回调可用：**点一下就能把订单
     * 置为已支付并触发业务解锁，全程不验签**。它存在的意义是在没有真实渠道时
     * 跑通「下单 → 支付 → 回调 → 解锁」，尤其是失败路径 —— 只测成功路径的系统
     * 往往在上线当天才发现失败没处理。
     *
     * **默认关闭，生产环境必须保持关闭。** 关闭时相关端点一律 404。
     *
     * 另有一层互斥保护：打开它会同时**禁用全部真实支付渠道**（见 PayOrderLogic.resolveClient）。
     * 于是生产环境一旦误开，收款立刻全断、几分钟内必被发现，而不是安静地让人白拿商品。
     */
    val MOCK_PAY_ENABLED: SettingDefinition<Boolean> = SettingDefinition.boolean(
        category = CATEGORY,
        key = "payment.mock.enabled",
        default = false,
        name = "模拟支付模式",
        description = "仅测试环境开启：开启后可通过模拟收银台直接把订单置为已支付，不产生真实扣款，也不校验渠道签名。生产环境必须关闭。",
    )

    /** 交给 `init.PaymentRuntimeBootstrap` 登记进 `SettingDefinitionRegistry`。 */
    val definitions: List<SettingDefinition<*>> = listOf(
        MOCK_PAY_ENABLED,
    )
}

/**
 * 按定义读当前值；缺失、解析失败一律回退定义默认值，所以返回非空。
 *
 * 走表而不是注入 `SystemSettingLogic`：读取点在控制器里，为一个布尔值多背一个依赖
 * 不划算。语义与 `SystemSettingLogic.get` 一致（同样的 parse + 默认值回退）。
 *
 * 回退到默认值这件事在这里尤其重要：读不到配置时得到的是**关闭**，
 * 也就是说数据库异常、行被误删都不会让模拟支付意外可用。
 */
suspend fun SettingDefinition<Boolean>.currentValue(): Boolean =
    SystemSettingTable.oneWhere { SystemSetting::settingKey eq key }
        ?.value?.let { parse(it) }
        ?: default
