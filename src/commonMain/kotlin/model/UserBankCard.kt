package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

/**
 * 用户绑定的银行卡（P4-B1）。卡号信封加密存储：
 *   - cardNoCiphertext：AES-256-GCM box（iv 内嵌）
 *   - encryptedDataKey：被主密钥/KMS 包裹的 data key
 *   - cardNoMasked：仅尾4位，列表/用户端只回这个
 *   - cardNoHash：HMAC，仅 (userId, hash) 去重，不可逆
 * 明文卡号绝不入库、绝不出接口；完整卡号仅后台 reveal 接口按权限解密 + 审计。
 */
@Serializable
@Table("user_bank_cards")
data class UserBankCard(
    @Id
    val id: Long = 0,
    val userId: Long,
    val holderName: String,
    val bankName: String,
    val bankCode: String? = null,
    val cardNoCiphertext: String,
    /** 预留：cryptography-kotlin 的 GCM box 已内嵌 iv，本版恒空；未来若拆分 nonce 用得上。 */
    val cardNoNonce: String? = null,
    val encryptedDataKey: String,
    val cardNoMasked: String,
    val cardNoHash: String,
    val encryptionVersion: Int = 1,
    /** 0=NORMAL 1=DISABLED */
    val status: Int = 0,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null,
    /** 软删：0=有效，>0=删除时间戳(ms)。 */
    val deletedAt: Long = 0,
)
