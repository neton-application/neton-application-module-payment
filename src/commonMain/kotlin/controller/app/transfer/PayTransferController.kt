package controller.app.transfer

import model.PayTransfer
import table.PayTransferTable
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.http.NotFoundException
import logic.PaymentErrorCodes


@Controller("/pay/transfer")
class PayTransferController {

    @Get("/sync")
    suspend fun sync(id: Long): PayTransfer? {
        val transfer = PayTransferTable.get(id)
            ?: throw NotFoundException(PaymentErrorCodes.PAY_TRANSFER_NOT_FOUND)

        // For v1, return current status from DB.
        // v2 will integrate with payment channel to sync real-time status.
        return transfer
    }
}
