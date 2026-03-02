package controller.app.channel

import model.PayChannel
import table.PayChannelTable
import neton.database.dsl.*
import neton.core.annotations.Controller
import neton.core.annotations.Get

@Controller("/pay/channel")
class PayChannelController {

    @Get("/get-enable-code-list")
    suspend fun getEnableCodeList(appId: Long): List<String> {
        return PayChannelTable.query {
            where {
                PayChannel::appId eq appId
                PayChannel::status eq 0
            }
        }.list().map { it.code }
    }
}
