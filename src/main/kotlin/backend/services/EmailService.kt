package backend.services

import backend.config.Env
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.slf4j.LoggerFactory
import java.util.Date
import java.util.Properties

class EmailService private constructor(
    private val session: Session,
    private val fromAddress: String,
    private val fromName: String,
    private val replyToAddress: String?
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    private val environmentPrefix = if (Env.get("DEPLOYMENT_ENV").equals("beta", true)) "[AITSM BETA] " else ""
    private val environmentNotice = if (environmentPrefix.isNotEmpty()) "\n\nThis message was sent from the AITSM beta environment. Do not treat beta activity as production activity." else ""

    fun sendPasswordReset(recipient: String, resetUrl: String): Boolean = sendPlain(
        recipient, "Reset your AITSM password",
        "A password reset was requested for your AITSM account. This single-use link expires in 30 minutes:\n\n$resetUrl\n\nIf you did not request this, ignore this message.$environmentNotice"
    )

    fun sendPasswordChanged(recipient: String): Boolean = sendPlain(
        recipient, "Your AITSM password changed",
        "Your AITSM password was changed. If this was not you, contact your system administrator immediately."
    )

    fun sendSecurityNotice(recipient: String, subject: String, message: String): Boolean = sendPlain(
        recipient, subject, "$message\n\nIf you did not expect this activity, contact your system administrator immediately."
    )

    private fun sendPlain(recipient: String, subjectLine: String, body: String): Boolean = runCatching {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromAddress, fromName)); setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false))
            subject = environmentPrefix + subjectLine; sentDate = Date(); setHeader("Auto-Submitted", "auto-generated")
            replyToAddress?.let { replyTo = arrayOf(InternetAddress(it)) }; setText(body, "UTF-8")
        }
        Transport.send(message); true
    }.getOrElse { error -> logger.error("Unable to send security email to {}: {}", recipient, error.message); false }

    fun sendInvitation(recipient: String, invitationUrl: String): Boolean = runCatching {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromAddress, fromName))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false))
            subject = environmentPrefix + "Your AITSM invitation"
            sentDate = Date()
            setHeader("Auto-Submitted", "auto-generated")
            replyToAddress?.let { replyTo = arrayOf(InternetAddress(it)) }
            setText("You have been invited to AITSM. This single-use link expires in 24 hours:\n\n$invitationUrl\n\nIf you did not expect this invitation, ignore this message.$environmentNotice", "UTF-8")
        }
        Transport.send(message)
        logger.info("Invitation email sent to {}", recipient)
        true
    }.getOrElse { error -> logger.error("Unable to send invitation email to {}: {}", recipient, error.message); false }

    fun sendVerificationCode(recipient: String, recipientName: String?, code: String): Boolean = runCatching {
        val safeName = recipientName?.trim()?.takeIf { it.isNotEmpty() } ?: "there"
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromAddress, fromName))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false))
            subject = environmentPrefix + "Verify your AITSM account"
            sentDate = Date()
            setHeader("Auto-Submitted", "auto-generated")
            setHeader("X-Auto-Response-Suppress", "All")
            replyToAddress?.let { replyTo = arrayOf(InternetAddress(it)) }

            val plainText = """
                Hello $safeName,

                Your AITSM verification code is: $code

                This code expires in 15 minutes. If you did not create this account, you can ignore this message.

                This is an automated message. Please do not reply.
            """.trimIndent()

            val html = """
                <!doctype html>
                <html lang="en">
                  <body style="margin:0;background:#f4f7fb;font-family:Arial,sans-serif;color:#172033">
                    <div style="max-width:560px;margin:32px auto;padding:32px;background:#ffffff;border-radius:12px;border:1px solid #dce3ee">
                      <h1 style="margin:0 0 20px;font-size:24px">Verify your AITSM account</h1>
                      <p>Hello ${escapeHtml(safeName)},</p>
                      <p>Use this verification code to finish creating your account:</p>
                      <div style="margin:24px 0;padding:16px;text-align:center;background:#eef4ff;border-radius:8px;font-size:32px;font-weight:700;letter-spacing:8px">$code</div>
                      <p>This code expires in 15 minutes. If you did not create this account, you can ignore this message.</p>
                      <p style="margin-top:28px;color:#687386;font-size:13px">This is an automated message. Please do not reply.</p>
                    </div>
                  </body>
                </html>
            """.trimIndent()

            setContent(MimeMultipart("alternative").apply {
                addBodyPart(MimeBodyPart().apply { setText(plainText, "UTF-8") })
                addBodyPart(MimeBodyPart().apply { setContent(html, "text/html; charset=UTF-8") })
            })
        }

        Transport.send(message)
        logger.info("Verification email sent to {}", recipient)
        true
    }.getOrElse { error ->
        logger.error("Unable to send verification email to {}: {}", recipient, error.message)
        false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EmailService::class.java)

        fun fromEnvironment(): EmailService? {
            val username = Env.get("SMTP_USERNAME")
            val password = Env.get("SMTP_PASSWORD")
            val fromAddress = Env.get("SMTP_FROM")

            if (username == null || password == null || fromAddress == null) {
                logger.warn("SMTP email delivery is disabled because SMTP_USERNAME, SMTP_PASSWORD, or SMTP_FROM is missing")
                return null
            }

            val host = Env.get("SMTP_HOST") ?: "smtp-relay.brevo.com"
            val port = Env.get("SMTP_PORT")?.toIntOrNull() ?: 587
            val startTls = (Env.get("SMTP_STARTTLS") ?: "true").toBooleanStrictOrNull() ?: true
            val properties = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.starttls.enable", startTls.toString())
                put("mail.smtp.starttls.required", startTls.toString())
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
                put("mail.smtp.writetimeout", "10000")
            }
            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            })

            return EmailService(
                session = session,
                fromAddress = fromAddress,
                fromName = Env.get("SMTP_FROM_NAME") ?: "AITSM No Reply",
                replyToAddress = Env.get("SMTP_REPLY_TO")
            )
        }

        private fun escapeHtml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
