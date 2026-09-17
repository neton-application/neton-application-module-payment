package init

import neton.core.annotations.Module

/** payment 模块声明锚点（MANIFEST-P3）。@Logic: 7 个 PayXxxLogic;
 *  runtime: init.PaymentRuntimeBootstrap (银行卡加密装配); migrations + 路由由 KSP manifest。 */
// dependsOn 含 infra 是因为**迁移顺序**：本模块的菜单改名 UPDATE 作用在 infra baseline 种下的
// system_menus 行上，漏声明时拓扑排序可能把本模块排在 infra 之前，那批 UPDATE 会匹配 0 行且不报错。
@Module(dependsOn = ["system", "infra"], migrations = true)
object PaymentModule
