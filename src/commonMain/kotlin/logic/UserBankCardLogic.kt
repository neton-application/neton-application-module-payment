package logic

import kotlinx.serialization.Serializable
import logic.crypto.BankCardCrypto
import model.UserBankCard
import model.PaySensitiveAuditLog
import table.UserBankCardTable
import table.PaySensitiveAuditLogTable
import neton.database.dsl.*
import neton.database.api.DbContext
import neton.database.dbContext
import neton.logging.Logger

/** 银行卡对外视图：只含掩码，绝不含 ciphertext / encrypted_data_key / hash / nonce。 */
@Serializable
data class BankCardView(
    val id: Long,
    val userId: Long,
    val holderName: String,
    val bankName: String,
    val bankCode: String?,
    val cardNoMasked: String,
    val status: Int,
    val createdAt: String?,
)

private fun UserBankCard.toView(): BankCardView = BankCardView(
    id = id,
    userId = userId,
    holderName = holderName,
    bankName = bankName,
    bankCode = bankCode,
    cardNoMasked = cardNoMasked,
    status = status,
    createdAt = createdAt,
)

/**
 * 用户银行卡能力（P4-B1）。卡号信封加密；用户端/列表只回 masked；
 * 完整卡号仅 [adminRevealCardNo]（后台打款权限）解密 + 审计日志。
 *
 * 非 @Logic：由 [init.PaymentRuntimeBootstrap] 手动构造并 ctx.bind（注入 [BankCardCrypto]）。
 */
class UserBankCardLogic(
    private val log: Logger,
    private val crypto: BankCardCrypto?,
    private val db: DbContext = dbContext(),
) {
    private fun requireCrypto(): BankCardCrypto = crypto
        ?: throw IllegalStateException("wallet card encryption not configured (env NETON_WALLET_CARD_ENC_KEY missing)")

    /** 绑卡：信封加密卡号入库，返回 masked 视图。同一用户重复卡号复用已有有效记录（幂等友好）。 */
    suspend fun bindBankCard(
        userId: Long,
        holderName: String,
        bankName: String,
        bankCode: String?,
        cardNo: String,
    ): BankCardView {
        requireParam(holderName.isNotBlank()) { "holderName is required" }
        requireParam(bankName.isNotBlank()) { "bankName is required" }
        requireParam(cardNo.isNotBlank()) { "cardNo is required" }
        val c = requireCrypto()
        val hash = c.hashOf(cardNo)

        // 去重：同一用户同卡（有效）→ 复用，不重复存。
        UserBankCardTable.oneWhere {
            and(
                UserBankCard::userId eq userId,
                UserBankCard::cardNoHash eq hash,
                UserBankCard::deletedAt eq 0L,
            )
        }?.let { return it.toView() }

        val enc = c.encrypt(cardNo)
        val inserted = UserBankCardTable.insert(
            UserBankCard(
                userId = userId,
                holderName = holderName,
                bankName = bankName,
                bankCode = bankCode,
                cardNoCiphertext = enc.ciphertextBase64,
                encryptedDataKey = enc.wrappedDataKeyBase64,
                cardNoMasked = enc.masked,
                cardNoHash = enc.hash,
                encryptionVersion = enc.version,
                status = 0,
            )
        )
        log.info("bank-card.bound", mapOf("cardId" to inserted.id, "userId" to userId))
        return inserted.toView()
    }

    /** 我的银行卡列表（仅 masked）。 */
    suspend fun listMyBankCards(userId: Long): List<BankCardView> =
        UserBankCardTable.query {
            where {
                and(
                    UserBankCard::userId eq userId,
                    UserBankCard::deletedAt eq 0L,
                )
            }
            orderBy(UserBankCard::id.desc())
        }.list().map { it.toView() }

    /** 软删（仅能删自己的卡）。返回是否删除。 */
    suspend fun deleteBankCard(userId: Long, id: Long): Boolean {
        val now = nowMillis()
        val updated = UserBankCardTable.query {
            where {
                and(
                    UserBankCard::id eq id,
                    UserBankCard::userId eq userId,
                    UserBankCard::deletedAt eq 0L,
                )
            }
        }.update {
            set(UserBankCard::deletedAt, now)
        }
        if (updated > 0) log.info("bank-card.deleted", mapOf("cardId" to id, "userId" to userId))
        return updated > 0
    }

    /**
     * 后台解密完整卡号（仅人工打款/审核用途）。**绝非静默**：每次都写审计日志。
     * 普通接口永不返回完整卡号。
     */
    suspend fun adminRevealCardNo(op: OperatorContext, id: Long): String {
        val card = UserBankCardTable.get(id)
            ?: walletNotFound("bank card not found: $id")
        val full = requireCrypto().decrypt(card.cardNoCiphertext, card.encryptedDataKey)
        // 不可抵赖（P0/V007）：落 DB 敏感操作审计行 + 保留非静默日志。
        PaySensitiveAuditLogTable.insert(
            PaySensitiveAuditLog(
                operatorId = op.operatorId,
                operatorName = op.operatorName,
                operatorRole = op.operatorRole,
                action = "BANK_CARD_REVEAL",
                targetType = "BANK_CARD",
                targetId = id,
                targetUserId = card.userId,
                ip = op.ip,
                userAgent = op.userAgent,
                traceId = op.traceId,
            )
        )
        log.warn(
            "bank-card.reveal.audit",
            mapOf(
                "operatorId" to op.operatorId, "cardId" to id, "cardUserId" to card.userId,
                "masked" to card.cardNoMasked, "traceId" to (op.traceId ?: ""),
            ),
        )
        return full
    }
}

private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
