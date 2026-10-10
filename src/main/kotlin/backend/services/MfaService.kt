package backend.services

import backend.config.Env
import backend.config.MfaRecoveryCodesTable
import backend.config.MfaSecretsTable
import backend.security.PasswordHasher
import backend.repository.AuditRepository
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream

data class MfaEnrollment(val uri: String, val secret: String)

class MfaService(private val audit: AuditRepository? = null) {
    private val random = SecureRandom()
    private val key = loadKey()

    fun isEnabled(userId: Int): Boolean = transaction {
        MfaSecretsTable.selectAll().where { MfaSecretsTable.userId eq userId }.singleOrNull()?.get(MfaSecretsTable.enabled) == true
    }

    fun begin(userId: Int, email: String): MfaEnrollment {
        val secretBytes = ByteArray(20).also(random::nextBytes)
        val secret = Base32.encode(secretBytes)
        val encrypted = encrypt(secret)
        transaction {
            val exists = MfaSecretsTable.selectAll().where { MfaSecretsTable.userId eq userId }.any()
            if (exists) MfaSecretsTable.update({ MfaSecretsTable.userId eq userId }) {
                it[encryptedSecret] = encrypted; it[enabled] = false; it[lastUsedStep] = null; it[confirmedAt] = null
            } else MfaSecretsTable.insert {
                it[MfaSecretsTable.userId] = userId; it[encryptedSecret] = encrypted; it[enabled] = false
                it[lastUsedStep] = null; it[createdAt] = LocalDateTime.now(); it[confirmedAt] = null
            }
        }
        val issuer = URLEncoder.encode("AITSM", StandardCharsets.UTF_8)
        val account = URLEncoder.encode(email, StandardCharsets.UTF_8)
        return MfaEnrollment("otpauth://totp/$issuer:$account?secret=$secret&issuer=$issuer&algorithm=SHA1&digits=6&period=30", secret)
    }

    fun confirm(userId: Int, code: String): List<String> {
        require(verifyTotp(userId, code, requireEnabled = false)) { "Invalid authentication code" }
        transaction { MfaSecretsTable.update({ MfaSecretsTable.userId eq userId }) { it[enabled] = true; it[confirmedAt] = LocalDateTime.now() } }
        val recoveryCodes = regenerateRecoveryCodes(userId, auditEvent = false)
        audit?.log(userId, "Enabled MFA", "auth")
        return recoveryCodes
    }

    fun verifyLogin(userId: Int, code: String?, recovery: String?): Boolean = when {
        !code.isNullOrBlank() -> verifyTotp(userId, code, requireEnabled = true)
        !recovery.isNullOrBlank() -> consumeRecovery(userId, recovery)
        else -> false
    }

    fun regenerateRecoveryCodes(userId: Int, auditEvent: Boolean = true): List<String> {
        val raw = List(8) { ByteArray(9).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it).uppercase() } }
        transaction {
            MfaRecoveryCodesTable.deleteWhere { MfaRecoveryCodesTable.userId eq userId }
            raw.forEach { code -> MfaRecoveryCodesTable.insert {
                it[id] = UUID.randomUUID(); it[MfaRecoveryCodesTable.userId] = userId; it[codeHash] = PasswordHasher.hash(code); it[usedAt] = null
            } }
        }
        if (auditEvent) audit?.log(userId, "Regenerated MFA recovery codes", "auth")
        return raw
    }

    fun disable(userId: Int) = transaction {
        MfaRecoveryCodesTable.deleteWhere { MfaRecoveryCodesTable.userId eq userId }
        MfaSecretsTable.deleteWhere { MfaSecretsTable.userId eq userId }
    }.also { audit?.log(userId, "Disabled or administratively reset MFA", "auth") }

    fun qrDataUrl(uri: String): String {
        val matrix = QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 240, 240)
        val output = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix, "PNG", output)
        return "data:image/png;base64,${Base64.getEncoder().encodeToString(output.toByteArray())}"
    }

    private fun consumeRecovery(userId: Int, code: String): Boolean = transaction {
        val row = MfaRecoveryCodesTable.selectAll().where { MfaRecoveryCodesTable.userId eq userId }
            .firstOrNull { it[MfaRecoveryCodesTable.usedAt] == null && PasswordHasher.verify(code.trim().uppercase(), it[MfaRecoveryCodesTable.codeHash]) }
            ?: return@transaction false
        MfaRecoveryCodesTable.update({ MfaRecoveryCodesTable.id eq row[MfaRecoveryCodesTable.id] }) { it[usedAt] = LocalDateTime.now() }
        true
    }

    private fun verifyTotp(userId: Int, code: String, requireEnabled: Boolean): Boolean = transaction {
        if (!code.matches(Regex("\\d{6}"))) return@transaction false
        val row = MfaSecretsTable.selectAll().where { MfaSecretsTable.userId eq userId }.singleOrNull() ?: return@transaction false
        if (requireEnabled && !row[MfaSecretsTable.enabled]) return@transaction false
        val secret = Base32.decode(decrypt(row[MfaSecretsTable.encryptedSecret]))
        val current = Instant.now().epochSecond / 30
        val matched = (current - 1..current + 1).firstOrNull { step -> totp(secret, step) == code } ?: return@transaction false
        if ((row[MfaSecretsTable.lastUsedStep] ?: -1) >= matched) return@transaction false
        MfaSecretsTable.update({ MfaSecretsTable.userId eq userId }) { it[lastUsedStep] = matched }
        true
    }

    internal fun totp(secret: ByteArray, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = digest.last().toInt() and 0x0f
        val binary = ((digest[offset].toInt() and 0x7f) shl 24) or ((digest[offset + 1].toInt() and 0xff) shl 16) or
            ((digest[offset + 2].toInt() and 0xff) shl 8) or (digest[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    private fun encrypt(value: String): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)) }
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(value.toByteArray()))
    }
    private fun decrypt(value: String): String {
        val all = Base64.getDecoder().decode(value); val nonce = all.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)) }
        return String(cipher.doFinal(all.copyOfRange(12, all.size)))
    }
    private fun loadKey(): ByteArray {
        val configured = Env.get("MFA_ENCRYPTION_KEY")
        if (!configured.isNullOrBlank()) return Base64.getDecoder().decode(configured).also { require(it.size == 32) { "MFA_ENCRYPTION_KEY must be base64-encoded 32 bytes" } }
        return MessageDigest.getInstance("SHA-256").digest("aitsm-development-mfa-key".toByteArray())
    }
}

internal object Base32 {
    private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun encode(bytes: ByteArray): String { var buffer = 0; var bits = 0; val out = StringBuilder(); for (b in bytes) { buffer = (buffer shl 8) or (b.toInt() and 255); bits += 8; while (bits >= 5) { out.append(alphabet[(buffer shr (bits - 5)) and 31]); bits -= 5 } }; if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31]); return out.toString() }
    fun decode(value: String): ByteArray { var buffer = 0; var bits = 0; val out = mutableListOf<Byte>(); for (c in value.uppercase().filter { it != '=' }) { val i = alphabet.indexOf(c); require(i >= 0); buffer = (buffer shl 5) or i; bits += 5; if (bits >= 8) { out += ((buffer shr (bits - 8)) and 255).toByte(); bits -= 8 } }; return out.toByteArray() }
}
