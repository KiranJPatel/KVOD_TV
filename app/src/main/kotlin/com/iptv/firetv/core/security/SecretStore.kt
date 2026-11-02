package com.iptv.firetv.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface SecretCodec {
    fun encrypt(plain: String): String
    fun decrypt(cipherText: String): String
}

/** Secrets for one source. Stored only as an AES-256-GCM blob (key lives in Android Keystore). */
@Serializable
data class SourceSecrets(
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val userAgent: String? = null,
    val epgUrl: String? = null,
    val liveExt: String? = null,
    /** Guide shift in minutes applied when this source's XMLTV data is imported. */
    val epgOffsetMin: Int? = null
)

@Singleton
class SecretStore @Inject constructor() : SecretCodec {
    private val alias = "iptv_source_secrets_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String {
        val raw = Base64.decode(cipherText, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        return String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }
}

private val secretsJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
fun SecretCodec.seal(s: SourceSecrets): String = encrypt(secretsJson.encodeToString(s))
fun SecretCodec.open(blob: String?): SourceSecrets? =
    blob?.let { runCatching { secretsJson.decodeFromString<SourceSecrets>(decrypt(it)) }.getOrNull() }
