package fr.lifecontrol.app.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Enveloppe d'un export chiffré : JSON lisible, contenu opaque. */
@Serializable
data class EncryptedBackup(
    val format: String = FORMAT,
    val version: Int = 1,
    val kdf: String = KDF,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
) {
    companion object {
        const val FORMAT = "bullet-encrypted"
        const val KDF = "PBKDF2WithHmacSHA256"
    }
}

class WrongPasswordException : Exception("Mot de passe incorrect ou fichier altéré.")

/**
 * Chiffrement des exports par mot de passe : clé AES-256 dérivée par PBKDF2-HMAC-SHA256
 * (sel aléatoire de 16 octets), chiffrement authentifié AES-GCM (IV de 12 octets, tag de 128 bits).
 * Un mauvais mot de passe ou un fichier modifié est détecté par le tag GCM.
 */
object BackupCrypto {
    const val MIN_PASSWORD_LENGTH = 8
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun encrypt(plainText: String, password: CharArray, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(EncryptedBackup.FORMAT.toByteArray())
        val data = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val envelope = EncryptedBackup(
            iterations = iterations,
            salt = b64.encodeToString(salt),
            iv = b64.encodeToString(iv),
            data = b64.encodeToString(data),
        )
        return json.encodeToString(EncryptedBackup.serializer(), envelope)
    }

    /** @throws WrongPasswordException si le mot de passe est faux ou le fichier altéré. */
    fun decrypt(text: String, password: CharArray): String {
        val envelope = parse(text) ?: throw IllegalArgumentException("Ce fichier n'est pas un export chiffré.")
        require(envelope.kdf == EncryptedBackup.KDF && envelope.version == 1) { "Format de chiffrement inconnu." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, b64d.decode(envelope.salt), envelope.iterations),
            GCMParameterSpec(TAG_BITS, b64d.decode(envelope.iv)),
        )
        cipher.updateAAD(EncryptedBackup.FORMAT.toByteArray())
        return try {
            String(cipher.doFinal(b64d.decode(envelope.data)), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    fun isEncrypted(text: String): Boolean = parse(text) != null

    private fun parse(text: String): EncryptedBackup? =
        if (!text.contains(EncryptedBackup.FORMAT)) null
        else runCatching { json.decodeFromString(EncryptedBackup.serializer(), text) }.getOrNull()
            ?.takeIf { it.format == EncryptedBackup.FORMAT }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(EncryptedBackup.KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
