package logic

import dto.PageResponse
import model.PayNotifyTask
import table.PayNotifyTaskTable
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

class PayNotifyLogic(
    private val log: Logger,
    private val db: DbContext = dbContext()
) {

    suspend fun getDetail(id: Long): PayNotifyTask? {
        return PayNotifyTaskTable.get(id)
    }

    suspend fun page(
        page: Int,
        size: Int,
        appId: Long? = null,
        type: Int? = null,
        status: Int? = null
    ): PageResponse<PayNotifyTask> {
        val result = PayNotifyTaskTable.query {
            where {
                and(
                    whenPresent(appId) { PayNotifyTask::appId eq it },
                    whenPresent(type) { PayNotifyTask::type eq it },
                    whenPresent(status) { PayNotifyTask::status eq it }
                )
            }
            orderBy(PayNotifyTask::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun handlePayNotify(appId: Long, dataId: Long, merchantUrl: String) {
        val task = PayNotifyTask(
            appId = appId,
            type = 1, // Payment notify
            dataId = dataId,
            merchantUrl = merchantUrl,
            maxNotifyTimes = 5
        )
        val inserted = PayNotifyTaskTable.insert(task)
        log.info("Created pay notify task with id: ${inserted.id}, appId: $appId, dataId: $dataId")
        // TODO: Trigger async notification to merchant
    }

    suspend fun handleRefundNotify(appId: Long, dataId: Long, merchantUrl: String) {
        val task = PayNotifyTask(
            appId = appId,
            type = 2, // Refund notify
            dataId = dataId,
            merchantUrl = merchantUrl,
            maxNotifyTimes = 5
        )
        val inserted = PayNotifyTaskTable.insert(task)
        log.info("Created refund notify task with id: ${inserted.id}, appId: $appId, dataId: $dataId")
        // TODO: Trigger async notification to merchant
    }
}
