package fit.sigap.app.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenStore {
    suspend fun readRefreshToken(): String?

    suspend fun writeRefreshToken(token: String)

    suspend fun clearRefreshToken()

    suspend fun readSessionEnded(): Boolean

    suspend fun writeSessionEnded(ended: Boolean)
}

private val Context.sessionDataStore by preferencesDataStore("session")

private val RefreshTokenKey = stringPreferencesKey("refresh_token")
private val SessionEndedKey = booleanPreferencesKey("session_ended")

class KeystoreTokenStore(context: Context) : TokenStore {
    private val store = context.applicationContext.sessionDataStore

    override suspend fun readRefreshToken(): String? {
        val sealed = store.data.first()[RefreshTokenKey] ?: return null
        return try {
            open(sealed)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun writeRefreshToken(token: String) {
        val sealed = seal(token)
        store.edit { it[RefreshTokenKey] = sealed }
    }

    override suspend fun clearRefreshToken() {
        store.edit { it.remove(RefreshTokenKey) }
    }

    override suspend fun readSessionEnded(): Boolean = store.data.first()[SessionEndedKey] ?: false

    override suspend fun writeSessionEnded(ended: Boolean) {
        store.edit { it[SessionEndedKey] = ended }
    }

    private fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val body = cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    private fun open(sealed: String): String {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
        }
        return String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES))
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sigap_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
