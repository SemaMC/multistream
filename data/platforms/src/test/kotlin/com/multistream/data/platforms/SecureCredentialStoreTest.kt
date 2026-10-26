package com.multistream.data.platforms

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Local unit tests for [SecureCredentialStore].
 *
 * The Android Keystore is not available on the JVM, so these tests inject the internal
 * constructor with:
 *
 * - [InMemoryPreferencesDataStore], a real [DataStore] implementation that uses the same
 *   `edit`/`Preferences` API but keeps the state in memory, and
 * - a real AES/GCM cipher ([AesGcmCredentialCrypto]) whose keys come from [InMemoryKeyProvider]
 *   instead of the Keystore.
 *
 * Only key storage and file persistence are substituted; encryption, serialization and the
 * DataStore contract itself are the production code paths.
 */
class SecureCredentialStoreTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var keyProvider: InMemoryKeyProvider
    private lateinit var store: SecureCredentialStore

    @Before
    fun setUp() {
        dataStore = InMemoryPreferencesDataStore()
        keyProvider = InMemoryKeyProvider()
        store = newStore(dataStore, keyProvider)
    }

    @Test
    fun `save and load round trip credentials`() = runTest {
        val credentials = PlatformCredentials(
            platform = Platform.TWITCH,
            rtmpUrl = "rtmp://live.twitch.tv/app/",
            streamKey = "live_123456_abcdef"
        )

        store.save(credentials)

        assertEquals(credentials, store.load(Platform.TWITCH))
    }

    @Test
    fun `load returns null when nothing was stored`() = runTest {
        assertNull(store.load(Platform.YOUTUBE))
    }

    @Test
    fun `multiple platforms coexist`() = runTest {
        val twitch = PlatformCredentials(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "twitch-key")
        val youtube = PlatformCredentials(Platform.YOUTUBE, "rtmp://a.rtmp.youtube.com/live2/", "youtube-key")
        val kick = PlatformCredentials(Platform.KICK, "rtmps://fa723fc1.kick.com/app/", "kick-key")
        val custom = PlatformCredentials(Platform.CUSTOM, "rtmp://127.0.0.1:1935/live", "custom-key")

        listOf(twitch, youtube, kick, custom).forEach { store.save(it) }

        assertEquals(twitch, store.load(Platform.TWITCH))
        assertEquals(youtube, store.load(Platform.YOUTUBE))
        assertEquals(kick, store.load(Platform.KICK))
        assertEquals(custom, store.load(Platform.CUSTOM))
    }

    @Test
    fun `saving again replaces the previous credentials`() = runTest {
        val first = PlatformCredentials(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "old-key")
        val second = PlatformCredentials(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "new-key")

        store.save(first)
        store.save(second)

        assertEquals(second, store.load(Platform.TWITCH))
    }

    @Test
    fun `stored payload is encrypted and contains no plaintext`() = runTest {
        val credentials = PlatformCredentials(
            platform = Platform.TWITCH,
            rtmpUrl = "rtmp://live.twitch.tv/app/",
            streamKey = "super-secret-stream-key"
        )

        store.save(credentials)

        val prefs = dataStore.data.first()
        val storedIv = prefs[stringPreferencesKey("TWITCH_iv")]
        val storedData = prefs[stringPreferencesKey("TWITCH_data")]

        assertNotNull(storedIv)
        assertNotNull(storedData)
        assertFalse(storedData!!.contains("super-secret-stream-key"))
        assertFalse(storedData.contains("rtmp://live.twitch.tv"))
        assertFalse(String(storedData.fromBase64(), Charsets.UTF_8).contains("super-secret-stream-key"))
        assertTrue(storedIv!!.fromBase64().size >= 12)
        // GCM appends a 16-byte authentication tag to the ciphertext.
        assertEquals(
            CredentialSerializer.serialize(credentials).size + 16,
            storedData.fromBase64().size
        )
    }

    @Test
    fun `every save uses a fresh iv`() = runTest {
        val credentials = PlatformCredentials(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "key")

        store.save(credentials)
        val firstPrefs = dataStore.data.first()
        val firstIv = firstPrefs[stringPreferencesKey("TWITCH_iv")]
        val firstData = firstPrefs[stringPreferencesKey("TWITCH_data")]

        store.save(credentials)
        val secondPrefs = dataStore.data.first()
        val secondIv = secondPrefs[stringPreferencesKey("TWITCH_iv")]
        val secondData = secondPrefs[stringPreferencesKey("TWITCH_data")]

        assertNotEquals(firstIv, secondIv)
        assertNotEquals(firstData, secondData)
        assertEquals(credentials, store.load(Platform.TWITCH))
    }

    @Test
    fun `credentials survive a new store instance sharing the data store`() = runTest {
        val credentials = PlatformCredentials(Platform.FACEBOOK, "rtmps://live-api-s.facebook.com:443/rtmp/", "fb-key")
        store.save(credentials)

        val reopened = newStore(dataStore, keyProvider)

        assertEquals(credentials, reopened.load(Platform.FACEBOOK))
    }

    @Test
    fun `each platform is encrypted with its own key`() = runTest {
        val twitch = PlatformCredentials(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "twitch-key")
        val youtube = PlatformCredentials(Platform.YOUTUBE, "rtmp://a.rtmp.youtube.com/live2/", "youtube-key")
        store.save(twitch)
        store.save(youtube)

        keyProvider.forget("multistream.credential.TWITCH")

        assertNull(store.load(Platform.TWITCH))
        assertEquals(youtube, store.load(Platform.YOUTUBE))
    }

    @Test
    fun `load returns null when the encryption key is unavailable`() = runTest {
        store.save(PlatformCredentials(Platform.KICK, "rtmps://fa723fc1.kick.com/app/", "kick-key"))

        val otherDevice = newStore(dataStore, InMemoryKeyProvider())

        assertNull(otherDevice.load(Platform.KICK))
    }

    @Test
    fun `load returns null when the stored iv is malformed`() = runTest {
        store.save(PlatformCredentials(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "key"))

        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("TWITCH_iv")] = "AAAA"
        }

        assertNull(store.load(Platform.TWITCH))
    }

    @Test
    fun `load returns null when the stored payload is not base64`() = runTest {
        store.save(PlatformCredentials(Platform.TWITCH, "rtmp://live.twitch.tv/app/", "key"))

        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("TWITCH_data")] = "not base64!!"
        }

        assertNull(store.load(Platform.TWITCH))
    }

    @Test
    fun `base64 extensions round trip arbitrary bytes`() {
        val bytes = ByteArray(256) { it.toByte() }

        assertArrayEquals(bytes, bytes.toBase64().fromBase64())
    }

    @Test
    fun `serializer round trips values containing separators and newlines`() {
        val credentials = PlatformCredentials(
            platform = Platform.CUSTOM,
            rtmpUrl = "rtmp://host:1935/live?token=abc:def",
            streamKey = "key:with:colons\nand newlines="
        )

        val encoded = String(CredentialSerializer.serialize(credentials), Charsets.UTF_8)

        assertEquals(credentials, CredentialSerializer.deserialize(encoded))
    }

    @Test
    fun `serializer rejects malformed payloads`() {
        assertNull(CredentialSerializer.deserialize("garbage"))
        assertNull(CredentialSerializer.deserialize("v2:Zm9v:YmFy:YmF6"))
        assertNull(CredentialSerializer.deserialize("v1:@@@:@@@:@@@"))
    }

    private fun newStore(
        dataStore: DataStore<Preferences>,
        keyProvider: InMemoryKeyProvider
    ): SecureCredentialStore = SecureCredentialStore(
        dataStore = dataStore,
        crypto = AesGcmCredentialCrypto(keyProvider)
    )

    /**
     * In-memory [DataStore] used because `PreferenceDataStoreFactory` from DataStore 1.0.0
     * cannot rewrite its file on Windows: it renames a scratch file over the existing one and
     * `File.renameTo` refuses to replace an existing destination on that platform. The behavior
     * under test here is the credential store, not DataStore's own file I/O.
     */
    private class InMemoryPreferencesDataStore(
        initial: Preferences = emptyPreferences()
    ) : DataStore<Preferences> {

        private val state = MutableStateFlow(initial)
        private val mutex = Mutex()

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
    }

    private class InMemoryKeyProvider : SecretKeyProvider {
        private val keys = mutableMapOf<String, SecretKey>()

        override fun getOrCreateKey(alias: String): SecretKey = keys.getOrPut(alias) {
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }

        fun forget(alias: String) {
            keys.remove(alias)
        }
    }
}
