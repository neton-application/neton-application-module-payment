package controller.admin.wallet

import dto.PageResponse
import logic.ConsumptionBalanceAdminLogic
import logic.ConsumptionBalanceMove
import logic.ConsumptionBalanceMoveRequest
import logic.ConsumptionBalanceTransactionVO
import logic.ConsumptionBalanceUserVO
import neton.core.annotations.*
import neton.core.interfaces.Identity

@Controller("/pay/consumption-balance")
class ConsumptionBalanceController(private val balances: ConsumptionBalanceAdminLogic) {
    @Get("/page")
    @Permission("pay:consumption-balance:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
        @Query userId: String? = null,
        @Query username: String? = null,
    ): PageResponse<ConsumptionBalanceUserVO> =
        balances.page(pageNo.coerceAtLeast(1), pageSize.coerceIn(1, 100), userId, username)

    @Get("/transaction/page")
    @Permission("pay:consumption-balance:query")
    suspend fun transactions(
        @Query userId: String,
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 20,
    ): PageResponse<ConsumptionBalanceTransactionVO> =
        balances.transactionPage(userId, pageNo.coerceAtLeast(1), pageSize.coerceIn(1, 100))

    @Post("/manual-credit")
    @Permission("pay:consumption-balance:credit")
    suspend fun credit(identity: Identity, @Body request: ConsumptionBalanceMoveRequest) {
        balances.move(identity.id.toLong(), ConsumptionBalanceMove.MANUAL_CREDIT, request)
    }

    @Post("/gift")
    @Permission("pay:consumption-balance:gift")
    suspend fun gift(identity: Identity, @Body request: ConsumptionBalanceMoveRequest) {
        balances.move(identity.id.toLong(), ConsumptionBalanceMove.GIFT, request)
    }

    @Post("/revoke")
    @Permission("pay:consumption-balance:revoke")
    suspend fun revoke(identity: Identity, @Body request: ConsumptionBalanceMoveRequest) {
        balances.move(identity.id.toLong(), ConsumptionBalanceMove.REVOKE, request)
    }
}
