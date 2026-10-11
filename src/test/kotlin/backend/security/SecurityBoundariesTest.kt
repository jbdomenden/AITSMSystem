package backend.security

import backend.models.UserRole
import backend.services.MfaService
import backend.services.SecureImageProcessor
import backend.services.SessionService
import backend.services.ReauthScope
import io.ktor.http.ContentType
import io.ktor.http.headersOf
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecurityBoundariesTest {
    @Test fun `session hash does not expose raw token`() {
        val raw = "not-a-real-session-token"
        val hash = SessionService.sha256(raw)
        assertEquals(64, hash.length)
        assertNotEquals(raw, hash)
        assertFalse(hash.contains(raw))
    }

    @Test fun `production authentication cookie has required attributes`() {
        val cookie = SessionCookies.auth("raw", production = true)
        assertTrue(cookie.httpOnly)
        assertTrue(cookie.secure)
        assertEquals("/", cookie.path)
        assertEquals("Lax", cookie.extensions["SameSite"])
        assertFalse(SessionCookies.auth("raw", production = false).secure)
    }

    @Test fun `permission matrix enforces least privilege`() {
        assertTrue(Permission.TICKET_CREATE in PermissionMatrix.permissions(UserRole.END_USER))
        assertFalse(Permission.USER_INVITE in PermissionMatrix.permissions(UserRole.END_USER))
        assertTrue(Permission.ASSET_MANAGE in PermissionMatrix.permissions(UserRole.ASSET_MANAGER))
        assertFalse(Permission.ROLE_MANAGE in PermissionMatrix.permissions(UserRole.ASSET_MANAGER))
        assertTrue(Permission.AUDIT_READ in PermissionMatrix.permissions(UserRole.SUPERADMIN))
        assertFalse(Permission.USER_INVITE in PermissionMatrix.permissions(UserRole.SUPPORT_AGENT))
        assertFalse(Permission.ROLE_MANAGE in PermissionMatrix.permissions(UserRole.AUDITOR))
        assertTrue(Permission.AUDIT_READ in PermissionMatrix.permissions(UserRole.AUDITOR))
    }

    @Test fun `legacy identity headers are always treated as hostile`() {
        assertTrue(hasLegacyIdentityHeaders(headersOf("X-User-Id", "1")))
        assertTrue(hasLegacyIdentityHeaders(headersOf("X-User-Role", "SUPERADMIN")))
        assertFalse(hasLegacyIdentityHeaders(headersOf("User-Agent", "test")))
    }

    @Test fun `incomplete MFA session can recover without reaching protected pages`() {
        assertTrue(isMfaRecoveryPath("/login"))
        assertTrue(isMfaRecoveryPath("/login.html"))
        assertTrue(isMfaRecoveryPath("/login/html/login.html"))
        assertTrue(isMfaRecoveryPath("/shared/css/styles.css"))
        assertTrue(isMfaRecoveryPath("/api/auth/login"))
        assertTrue(isMfaRecoveryPath("/api/auth/mfa/enroll"))
        assertFalse(isMfaRecoveryPath("/dashboard-admin.html"))
        assertFalse(isMfaRecoveryPath("/api/tickets"))
        assertFalse(isMfaRecoveryPath("/metrics"))
        assertFalse(isMfaRecoveryPath("/uploads/profile-photos/example.png"))
    }

    @Test fun `public static assets do not perform session writes during parallel page loading`() {
        assertTrue(isPublicStaticAsset("/shared/css/styles.css", io.ktor.http.HttpMethod.Get))
        assertTrue(isPublicStaticAsset("/modules/common/js/app.js", io.ktor.http.HttpMethod.Get))
        assertTrue(isPublicStaticAsset("/profile/assets/default-avatar.png", io.ktor.http.HttpMethod.Get))
        assertFalse(isPublicStaticAsset("/dashboard-user.html", io.ktor.http.HttpMethod.Get))
        assertFalse(isPublicStaticAsset("/api/tickets", io.ktor.http.HttpMethod.Get))
        assertFalse(isPublicStaticAsset("/api/files/report.png", io.ktor.http.HttpMethod.Get))
        assertFalse(isPublicStaticAsset("/uploads/profile-photos/example.png", io.ktor.http.HttpMethod.Get))
        assertFalse(isPublicStaticAsset("/shared/css/styles.css", io.ktor.http.HttpMethod.Post))
    }

    @Test fun `server classifies protected application pages for both portals`() {
        assertEquals(PageAudience.ADMIN, protectedPageAudience("/dashboard-admin"))
        assertEquals(PageAudience.ADMIN, protectedPageAudience("/dashboard-admin.html"))
        assertEquals(PageAudience.ADMIN, protectedPageAudience("/knowledge/html/knowledge.html"))
        assertEquals(PageAudience.END_USER, protectedPageAudience("/dashboard-user/html/dashboard-user.html"))
        assertEquals(PageAudience.AUTHENTICATED, protectedPageAudience("/profile/html/profile.html"))
        assertEquals(null, protectedPageAudience("/login/html/login.html"))
        assertEquals(null, protectedPageAudience("/modules/sidebar/html/sidebar.html"))
        assertEquals(null, protectedPageAudience("/api/tickets"))
        assertEquals(null, protectedPageAudience("/api/knowledge"))
        assertEquals(null, protectedPageAudience("/dashboard-admin/js/dashboard.js"))
    }

    @Test fun `admin portal role and landing page include IT administrators`() {
        assertTrue(isAdminPortalRole(UserRole.ADMIN))
        assertTrue(isAdminPortalRole(UserRole.IT_ADMIN))
        assertTrue(isAdminPortalRole(UserRole.SUPERADMIN))
        assertFalse(isAdminPortalRole(UserRole.END_USER))
        assertEquals("/dashboard-admin", portalHome(UserRole.IT_ADMIN))
        assertEquals("/dashboard-user", portalHome(UserRole.END_USER))
    }

    @Test fun `authentication pages redirect completed sessions instead of showing login again`() {
        assertTrue(isAuthenticationPage("/"))
        assertTrue(isAuthenticationPage("/login"))
        assertTrue(isAuthenticationPage("/login.html"))
        assertTrue(isAuthenticationPage("/login/html/login.html"))
        assertFalse(isAuthenticationPage("/api/auth/login"))
        assertFalse(isAuthenticationPage("/dashboard-user"))
    }

    @Test fun `reauthentication scopes are exact and cannot be widened by the client`() {
        assertEquals(ReauthScope.ADMIN_ACCESS, ReauthScope.parse("admin-access"))
        assertEquals(ReauthScope.ACCOUNT_DELETE, ReauthScope.parse("account-delete"))
        assertEquals(null, ReauthScope.parse("ADMIN_ACCESS"))
        assertEquals(null, ReauthScope.parse("*"))
    }

    @Test fun `rate limiter rejects excess attempts`() {
        val limiter = RateLimiter(2, 60_000)
        assertTrue(limiter.allow("ip")); assertTrue(limiter.allow("ip")); assertFalse(limiter.allow("ip"))
        assertTrue(limiter.allow("other-ip"))
    }

    @Test fun `totp follows RFC 6238 SHA1 vector truncated to six digits`() {
        assertEquals("287082", MfaService().totp("12345678901234567890".toByteArray(), 1))
    }

    @Test fun `image processor decodes and reencodes png`() {
        val source = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()
        val result = SecureImageProcessor.process("avatar.png", ContentType.Image.PNG, source)
        assertEquals("png", result.extension)
        assertTrue(result.bytes.isNotEmpty())
        assertTrue(ImageIO.read(result.bytes.inputStream()) != null)
    }

    @Test fun `image processor rejects active and spoofed content`() {
        assertFailsWith<IllegalArgumentException> {
            SecureImageProcessor.process("avatar.png", ContentType.Image.PNG, "<svg onload=alert(1)>".toByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            SecureImageProcessor.process("avatar.svg", ContentType.parse("image/svg+xml"), "<svg/>".toByteArray())
        }
    }
}
