package logic.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 信封加密的密钥管理边界（P4-B1）。
 *
 * 用「主密钥」加/解密「每张卡的 data key」（信封外层）。业务代码不绑定任何具体
 * KMS：生产用 KMS/Vault/云厂商密钥服务实现 [KmsWalletCryptoKeyProvider]（留实现位），
 * 开发用 env 主密钥实现 [EnvWalletCryptoKeyProvider]。
 *
 * data key 的明文仅在内存短暂存在；DB 只存被包裹后的 [wrapDataKey] 结果。
 */
interface WalletCryptoKeyProvider {
    /** 用主密钥包裹（加密）一个明文 data key。返回值入库（encrypted_data_key）。 */
    suspend fun wrapDataKey(plainDataKey: ByteArray): ByteArray

    /** 用主密钥解开被包裹的 data key（仅后台打款 reveal / 解密时调用）。 */
    suspend fun unwrapDataKey(wrappedDataKey: ByteArray): ByteArray

    /** 加密方案版本，写入 encryption_version，便于密钥轮换 / 算法升级。 */
    val version: Int
}

/**
 * 开发/本地实现：主密钥来自 env `NETON_WALLET_CARD_ENC_KEY`（32 bytes base64，AES-256）。
 * 用主密钥 AES-256-GCM 包裹 data key。**仅开发环境**；生产请用 KMS provider。
 */
class EnvWalletCryptoKeyProvider(
    masterKeyBase64: String,
    override val version: Int = 1,
) : WalletCryptoKeyProvider {

    @OptIn(ExperimentalEncodingApi::class)
    private val masterKey: ByteArray = Base64.decode(masterKeyBase64).also {
        require(it.size == 32) { "NETON_WALLET_CARD_ENC_KEY must decode to 32 bytes (AES-256), got ${it.size}" }
    }

    override suspend fun wrapDataKey(plainDataKey: ByteArray): ByteArray =
        AesGcm.encrypt(masterKey, plainDataKey)

    override suspend fun unwrapDataKey(wrappedDataKey: ByteArray): ByteArray =
        AesGcm.decrypt(masterKey, wrappedDataKey)

    companion object {
        const val ENV_KEY_NAME = "NETON_WALLET_CARD_ENC_KEY"
    }
}

/**
 * AES-256-GCM 原语（cryptography-kotlin，Native via CommonCrypto/OpenSSL，不手写 crypto）。
 * 输出 box = iv ‖ ciphertext ‖ tag（nonce 由库随机生成并内嵌，故无需单独管理 nonce）。
 */
internal object AesGcm {
    private val gcm = CryptographyProvider.Default.get(AES.GCM)

    /** 随机生成一个 256-bit data key（RAW 字节）。 */
    fun generateDataKey(): ByteArray =
        gcm.keyGenerator(AES.Key.Size.B256)
            .generateKeyBlocking()
            .encodeToByteArrayBlocking(AES.Key.Format.RAW)

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray =
        gcm.keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher()
            .encryptBlocking(plaintext)

    fun decrypt(key: ByteArray, box: ByteArray): ByteArray =
        gcm.keyDecoder()
            .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher()
            .decryptBlocking(box)
}
