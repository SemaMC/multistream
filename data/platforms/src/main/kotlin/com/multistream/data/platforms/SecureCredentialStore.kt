package com.multistream.data.platforms

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes [this] as a standard Base64 string (no line wrapping).
 *
 * `java.util.Base64` is used instead of `android.util.Base64` so the codec also works in JVM
 * unit tests. It is available from API 26 and this module's minSdk is 29.
 */
fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

/** Decodes a Base64 string produced by [toBase64]. Throws [IllegalArgumentException] if invalid. */
fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)

/**
 * Persists platform credentials encrypted with AES/GCM using a key held by the Android Keystore.
 *
 * The Android Keystore never exports the key material: it is generated on the device, bound to
 * this application and used only through [Cipher]. What is written to DataStore is the GCM IV
 * and the ciphertext (both Base64 encoded), one pair per [Platform]:
 *
 * - `"<PLATFORM>_iv"`   -> Base64 initialisation vector
 * - `"<PLATFORM>_data"` -> Base64 ciphertext
 *
 * Each [save] generates a fresh IV, and credentials for different platforms use separate keys
 * (one Keystore alias per platform).
 *
 * [load] returns `null` when nothing is stored for the platform, and also when the stored entry
 * cannot be recovered (key removed, data tampered with, malformed Base64). A lost key is
 * indistinguishable from missing credentials from the caller's point of view: the user has to
 * authenticate again.
 *
 * The Hilt-injected constructor is the one taking a [Context]; the internal constructor exists so
 * JVM tests can supply a [DataStore] implementation and a pluggable [CredentialCrypto] (the Android
 * Keystore is not available in local unit tests).
 */
@Singleton
class SecureCredentialStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val crypto: CredentialCrypto
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        dataStore = context.platformCredentialsDataStore,
        crypto = AesGcmCredentialCrypto(AndroidKeystoreKeyProvider())
    )

    /** Encrypts and stores [credentials], replacing any previous entry for the same platform. */
    suspend fun save(credentials: PlatformCredentials) {
        val payload = crypto.encrypt(
            alias = keyAlias(credentials.platform),
            plaintext = CredentialSerializer.serialize(credentials)
        )
        dataStore.edit { prefs ->
            prefs[ivKey(credentials.platform)] = payload.iv.toBase64()
            prefs[dataKey(credentials.platform)] = payload.ciphertext.toBase64()
        }
    }

    /** Decrypts the stored credentials for [platform], or returns `null` if none/not readable. */
    suspend fun load(platform: Platform): PlatformCredentials? {
        val prefs = dataStore.data.first()
        return try {
            val iv = prefs[ivKey(platform)]?.fromBase64() ?: return null
            val ciphertext = prefs[dataKey(platform)]?.fromBase64() ?: return null
            val plaintext = crypto.decrypt(
                alias = keyAlias(platform),
                payload = EncryptedPayload(iv, ciphertext)
            )
            CredentialSerializer.deserialize(String(plaintext, Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun ivKey(platform: Platform) = stringPreferencesKey("${platform.name}_iv")

    private fun dataKey(platform: Platform) = stringPreferencesKey("${platform.name}_data")

    private fun keyAlias(platform: Platform) = "$KEY_ALIAS_PREFIX${platform.name}"

    companion object {
        private const val KEY_ALIAS_PREFIX = "multistream.credential."
    }
}

/** Raw output of a symmetric encryption: the IV plus the authenticated ciphertext. */
internal data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
}

/** Supplies the AES key for [alias], creating it on first use. */
internal interface SecretKeyProvider {
    fun getOrCreateKey(alias: String): SecretKey
}

/**
 * [SecretKeyProvider] backed by the Android Keystore. The Keystore is opened lazily so this
 * class can be referenced from JVM tests without touching Android APIs.
 */
internal class AndroidKeystoreKeyProvider(
    private val keyStoreName: String = ANDROID_KEYSTORE
) : SecretKeyProvider {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(keyStoreName).apply { load(null) }
    }

    @Synchronized
    override fun getOrCreateKey(alias: String): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreName)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
    }
}

/** Symmetric encryption used to protect credentials at rest. */
internal interface CredentialCrypto {
    fun encrypt(alias: String, plaintext: ByteArray): EncryptedPayload
    fun decrypt(alias: String, payload: EncryptedPayload): ByteArray
}

/**
 * AES in GCM mode (authenticated encryption) with a 128-bit tag. The key comes from
 * [keyProvider]; on Android that provider is the Android Keystore.
 */
internal class AesGcmCredentialCrypto(
    private val keyProvider: SecretKeyProvider
) : CredentialCrypto {

    override fun encrypt(alias: String, plaintext: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey(alias))
        return EncryptedPayload(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    override fun decrypt(alias: String, payload: EncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider.getOrCreateKey(alias),
            GCMParameterSpec(TAG_LENGTH_BITS, payload.iv)
        )
        return cipher.doFinal(payload.ciphertext)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}

/**
 * Versioned, delimiter-safe representation of [PlatformCredentials] as UTF-8 bytes.
 *
 * Format: `v1:<base64(platform)>:<base64(rtmpUrl)>:<base64(streamKey)>`. Base64 keeps the fields
 * free of the `:` separator, so URLs or stream keys containing separators or newlines survive a
 * round trip. Unknown versions or malformed fields deserialize to `null` instead of throwing.
 */
internal object CredentialSerializer {

    private const val VERSION = "v1"
    private const val SEPARATOR = ":"
    private const val FIELD_COUNT = 4

    fun serialize(credentials: PlatformCredentials): ByteArray = buildString {
        append(VERSION).append(SEPARATOR)
        append(credentials.platform.name.encodeToBase64()).append(SEPARATOR)
        append(credentials.rtmpUrl.encodeToBase64()).append(SEPARATOR)
        append(credentials.streamKey.encodeToBase64())
    }.toByteArray(Charsets.UTF_8)

    fun deserialize(encoded: String): PlatformCredentials? {
        val fields = encoded.split(SEPARATOR)
        if (fields.size != FIELD_COUNT || fields[0] != VERSION) return null

        return try {
            PlatformCredentials(
                platform = Platform.valueOf(fields[1].fromBase64().decodeToString()),
                rtmpUrl = fields[2].fromBase64().decodeToString(),
                streamKey = fields[3].fromBase64().decodeToString()
            )
        } catch (e: IllegalArgumentException) {
            // Invalid Base64 or an unknown platform name: treat as unreadable data.
            null
        }
    }

    private fun String.encodeToBase64(): String = toByteArray(Charsets.UTF_8).toBase64()
}

private val Context.platformCredentialsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "platform_credentials"
)
