package fr.lifecontrol.app.data

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.data.repository.BackupCrypto
import fr.lifecontrol.app.data.repository.WrongPasswordException
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {

    private val plain = """{"schemaVersion":1,"habits":[{"name":"Café ☕"}]}"""

    // Itérations réduites pour garder des tests rapides ; le défaut de production est plus élevé.
    private fun encrypt(password: String) = BackupCrypto.encrypt(plain, password.toCharArray(), iterations = 1_000)

    @Test
    fun roundTrip() {
        val encrypted = encrypt("correct horse")
        assertThat(encrypted).doesNotContain("Café")
        assertThat(BackupCrypto.isEncrypted(encrypted)).isTrue()
        assertThat(BackupCrypto.decrypt(encrypted, "correct horse".toCharArray())).isEqualTo(plain)
    }

    @Test
    fun wrongPassword_isDetected() {
        val encrypted = encrypt("correct horse")
        assertThrows(WrongPasswordException::class.java) { BackupCrypto.decrypt(encrypted, "wrong horse".toCharArray()) }
    }

    @Test
    fun tamperedData_isDetected() {
        val encrypted = encrypt("correct horse")
        val data = Regex("\"data\":\"([^\"]+)\"").find(encrypted)!!.groupValues[1]
        val flipped = (if (data[0] == 'A') 'B' else 'A') + data.substring(1)
        assertThrows(WrongPasswordException::class.java) {
            BackupCrypto.decrypt(encrypted.replace(data, flipped), "correct horse".toCharArray())
        }
    }

    @Test
    fun sameInput_givesDifferentCiphertexts() {
        assertThat(encrypt("pw-12345")).isNotEqualTo(encrypt("pw-12345"))
    }

    @Test
    fun plainExport_isNotEncrypted() {
        assertThat(BackupCrypto.isEncrypted(plain)).isFalse()
    }
}
