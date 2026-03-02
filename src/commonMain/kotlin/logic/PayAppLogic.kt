package logic

import dto.PageResponse
import model.PayApp
import table.PayAppTable
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

class PayAppLogic(
    private val log: Logger,
    private val db: DbContext = dbContext()
) {

    suspend fun create(app: PayApp): Long {
        val inserted = PayAppTable.insert(app)
        log.info("Created pay app with id: ${inserted.id}, name: ${app.name}")
        return inserted.id
    }

    suspend fun update(app: PayApp) {
        PayAppTable.update(app)
        log.info("Updated pay app with id: ${app.id}")
    }

    suspend fun delete(id: Long) {
        PayAppTable.destroy(id)
        log.info("Deleted pay app with id: $id")
    }

    suspend fun get(id: Long): PayApp? {
        return PayAppTable.get(id)
    }

    suspend fun updateStatus(id: Long, status: Int) {
        val app = PayAppTable.get(id) ?: return
        PayAppTable.update(app.copy(status = status))
        log.info("Updated pay app status with id: $id, status: $status")
    }

    suspend fun list(
        name: String? = null,
        status: Int? = null
    ) = PayAppTable.query {
        where {
            and(
                whenNotBlank(name) { PayApp::name like "%$it%" },
                whenPresent(status) { PayApp::status eq it }
            )
        }
        orderBy(PayApp::id.desc())
    }.list()

    suspend fun page(
        page: Int,
        size: Int,
        name: String? = null,
        status: Int? = null
    ): PageResponse<PayApp> {
        val result = PayAppTable.query {
            where {
                and(
                    whenNotBlank(name) { PayApp::name like "%$it%" },
                    whenPresent(status) { PayApp::status eq it }
                )
            }
            orderBy(PayApp::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }
}
