package logic.crypto

import neton.security.internal.HmacSha256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 银行卡卡号信封加密结果（入库字段）。卡号明文绝不入库、绝不出接口。 */
data class EncryptedCard(
    val ciphertextBase64: String,     // card_no_ciphertext：AES-256-GCM box（iv 内嵌）
    val wrappedDataKeyBase64: String, // encrypted_data_key：被主密钥/KMS 包裹的 data key
    val masked: String,               // card_no_masked：仅尾4位
    val hash: String,                 // card_no_hash：HMAC，仅去重、不可逆、不可用于打款
    val version: Int,                 // encryption_version
)

/**
 * 银行卡卡号信封加密（P4-B1）：
 *   1. 每张卡随机生成 data key
 *   2. data key 用 AES-256-GCM 加密卡号
 *   3. data key 再被 [WalletCryptoKeyProvider]（KMS/env 主密钥）包裹
 * 即使 DB 泄露也拿不到明文卡号；换 KMS 只需重包 data key，无需重新加密所有卡号。
 */
@OptIn(ExperimentalEncodingApi::class)
class BankCardCrypto(
    private val keyProvider: WalletCryptoKeyProvider,
    private val hmacKey: ByteArray,
) {
    /** 加密卡号 → 入库字段。入参为用户提交的卡号（允许带空格/连字符）。 */
    suspend fun encrypt(cardNo: String): EncryptedCard {
        val normalized = normalize(cardNo)
        require(normalized.length in 8..19 && normalized.all { it.isDigit() }) {
            "invalid card number"
        }
        val dataKey = AesGcm.generateDataKey()
        val box = AesGcm.encrypt(dataKey, normalized.encodeToByteArray())
        val wrapped = keyProvider.wrapDataKey(dataKey)
        return EncryptedCard(
            ciphertextBase64 = Base64.encode(box),
            wrappedDataKeyBase64 = Base64.encode(wrapped),
            masked = mask(normalized),
            hash = HmacSha256.signHex(hmacKey, normalized.encodeToByteArray()),
            version = keyProvider.version,
        )
    }

    /** 解密完整卡号（仅后台打款 reveal，需权限 + 写 audit log）。 */
    suspend fun decrypt(ciphertextBase64: String, wrappedDataKeyBase64: String): String {
        val dataKey = keyProvider.unwrapDataKey(Base64.decode(wrappedDataKeyBase64))
        return AesGcm.decrypt(dataKey, Base64.decode(ciphertextBase64)).decodeToString()
    }

    /** 卡号去重 hash（绑卡时查 (user_id, hash) 是否已存在）。 */
    fun hashOf(cardNo: String): String =
        HmacSha256.signHex(hmacKey, normalize(cardNo).encodeToByteArray())

    companion object {
        /** 去掉空格与连字符。 */
        fun normalize(cardNo: String): String = cardNo.filter { !it.isWhitespace() && it != '-' }

        /** 掩码：仅保留尾 4 位，如 `**** **** **** 1234`。 */
        fun mask(normalized: String): String = "**** **** **** ${normalized.takeLast(4)}"
    }
}
