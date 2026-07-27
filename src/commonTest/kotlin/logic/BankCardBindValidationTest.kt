package logic

import kotlinx.coroutines.runBlocking
import logic.crypto.BankCardCrypto
import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.database.sql.BuiltSql
import neton.database.api.DbContext
import neton.database.api.QueryInterceptor
import neton.database.api.Row
import neton.database.sql.Dialect
import neton.database.dsl.SelectBuilder
import neton.database.api.Table
import neton.database.dsl.TableRef
import neton.logging.Fields
import neton.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object NoopLogger : Logger {
    override fun trace(msg: String, fields: Fields) {}
    override fun debug(msg: String, fields: Fields) {}
    override fun info(msg: String, fields: Fields) {}
    override fun warn(msg: String, fields: Fields, cause: Throwable?) {}
    override fun error(msg: String, fields: Fields, cause: Throwable?) {}
}

/** 任何一次数据库访问都直接炸掉：用来证明非法卡号在碰库之前就被拒了。 */
private object ExplodingDb : DbContext {
    override val dialect: Dialect get() = fail()
    override val interceptors: List<QueryInterceptor> get() = emptyList()
    override suspend fun fetchAll(sql: String, params: Map<String, Any?>): List<Row> = fail()
    override suspend fun execute(sql: String, params: Map<String, Any?>): Long = fail()
    override fun <T : Any> from(table: Table<T, *>): Pair<SelectBuilder, TableRef<T>> = fail()
    override suspend fun query(built: BuiltSql): List<Row> = fail()
    override suspend fun executeBuilt(built: BuiltSql): Long = fail()
    override suspend fun <R> transaction(block: suspend DbContext.() -> R): R = fail()
    private fun fail(): Nothing =
        throw AssertionError("invalid card number must be rejected before touching the database")
}

/**
 * 绑卡入参校验（A2）。
 *
 * 生产事故：卡号格式的唯一校验藏在 `BankCardCrypto.encrypt` 的裸 `require` 里，
 * 非法卡号抛 `IllegalArgumentException` → HTTP 500，客户端只显示无原因的「绑卡失败」
 * （2026-07-26 生产 7 次）。校验必须在业务层，且非法输入不得触发 hash / encrypt / 查库。
 *
 * `crypto = null` 是刻意的：一旦流程越过校验走到加密，就会抛
 * `IllegalStateException("wallet card encryption not configured")` —— 用异常类型区分
 * 「在校验处被拒」和「已经走过校验」。
 */
class BankCardBindValidationTest {

    private val logic = UserBankCardLogic(NoopLogger, crypto = null, db = ExplodingDb)

    private suspend fun bind(cardNo: String) =
        logic.bindBankCard(
            userId = 1L,
            holderName = "张三",
            bankName = "招商银行",
            bankCode = null,
            cardNo = cardNo,
        )

    private suspend fun assertRejected(cardNo: String, hint: String) {
        val e = assertFailsWith<HttpException>(hint) { bind(cardNo) }
        assertEquals(NetonErrorCode.INVALID_PARAMS, e.code, "$hint：必须是 400 而不是 500")
    }

    /** 越过校验 = 走到了加密阶段（crypto 未配置 → IllegalStateException）。 */
    private suspend fun assertAcceptedByValidation(cardNo: String, hint: String) {
        val e = assertFailsWith<IllegalStateException>(hint) { bind(cardNo) }
        assertTrue(
            e.message?.contains("encryption not configured") == true,
            "$hint：期望停在加密阶段，实际 ${e.message}",
        )
    }

    @Test
    fun `spaces and hyphens are normalized before validation`() = runBlocking {
        assertEquals("6222021234567890", BankCardCrypto.normalize("6222 0212 3456 7890"))
        assertEquals("6222021234567890", BankCardCrypto.normalize("6222-0212-3456-7890"))
        // 带分隔符的合法卡号必须通过校验（用户从短信里粘贴时常见）
        assertAcceptedByValidation("6222 0212 3456 7890", "带空格的合法卡号")
        assertAcceptedByValidation("6222-0212-3456-7890", "带连字符的合法卡号")
    }

    @Test
    fun `length boundaries 8 and 19 are accepted`() = runBlocking {
        assertAcceptedByValidation("1".repeat(8), "8 位下边界")
        assertAcceptedByValidation("1".repeat(19), "19 位上边界")
    }

    @Test
    fun `out of range lengths are rejected with 400`() = runBlocking {
        assertRejected("1".repeat(7), "7 位")
        assertRejected("1".repeat(20), "20 位")
    }

    @Test
    fun `non digit input is rejected with 400`() = runBlocking {
        assertRejected("6222a21234567890", "含字母")
        assertRejected("6222/0212/3456/7890", "含非法分隔符")
    }

    @Test
    fun `blank card number is rejected with 400`() = runBlocking {
        assertRejected("   ", "全空白")
    }
}
