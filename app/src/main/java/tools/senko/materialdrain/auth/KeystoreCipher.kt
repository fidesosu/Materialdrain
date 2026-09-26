package tools.senko.materialdrain.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG_CIPHER = "KeystoreCipher"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128

/** AES-256-GCM encryption with a key which never leaves the Android Keystore. */
class KeystoreCipher(private val alias: String = "materialdrain_session_key") {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(_root_ide_package_.tools.senko.materialdrain.auth.ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Returns "base64(iv):base64(ciphertext)". */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /** Returns null when the value cannot be decrypted, e.g. the key was lost after a backup restore. */
    fun decrypt(encoded: String): String? {
        return try {
            val (ivPart, dataPart) = encoded.split(':', limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivPart, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(dataPart, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG_CIPHER, "Could not decrypt stored value: ${e.javaClass.simpleName}")
            null
        }
    }
}
