package init

import neton.core.annotations.Module

/** payment 模块声明锚点（MANIFEST-P3）。@Logic: 7 个 PayXxxLogic;
 *  runtime: init.PaymentRuntimeBootstrap (银行卡加密装配); migrations + 路由由 KSP manifest。 */
@Module(dependsOn = ["system"])
object PaymentModule
