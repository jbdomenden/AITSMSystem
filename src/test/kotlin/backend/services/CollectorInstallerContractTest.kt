package backend.services

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectorInstallerContractTest {
    @Test fun `windows installer is packaged as a self contained executable`() {
        val installer = Path.of("src/main/resources/collector/AITSMCollectorSetup.exe")
        assertTrue(Files.size(installer) > 1_000_000)
        Files.newInputStream(installer).use { input ->
            assertContentEquals(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte()), input.readNBytes(2))
        }
    }

    @Test fun `installer protects credentials and installs an automatic service`() {
        val source = Files.readString(Path.of("collector/windows/installer/Program.cs"))
        assertTrue(source.contains("CryptProtectData"))
        assertTrue(source.contains("private const int LocalMachine = 0x4"))
        assertTrue(source.contains("/inheritance:r"))
        assertTrue(source.contains("\"start=\", \"auto\""))
        assertTrue(source.contains("config.Scope == \"LAN\""))
        assertFalse(source.contains("Console.WriteLine(config.Token)"))
    }

    @Test fun `self service enrollment remains owner and role scoped`() {
        val service = Files.readString(Path.of("src/main/kotlin/backend/services/MonitoringService.kt"))
        val routes = Files.readString(Path.of("src/main/kotlin/backend/routes/MonitoringRoutes.kt"))
        assertTrue(service.contains("val scope = if (admin) requestedScope else \"SELF\""))
        assertTrue(service.contains("if (collector.scope != \"LAN\") return null"))
        assertTrue(service.contains("Self-device collectors may report exactly one device"))
        assertTrue(routes.contains("service.canDownloadPackage(request.enrollmentCode, actorId)"))
        assertTrue(routes.contains("service.revokeOwned(id, actorId)"))
    }

    @Test fun `enrollment migration stores hashes and ownership without raw secrets`() {
        val migration = Files.readString(Path.of("db/migrations/V008__collector_self_service_enrollment.sql"))
        assertTrue(migration.contains("code_hash VARCHAR(64) NOT NULL UNIQUE"))
        assertTrue(migration.contains("requested_by INTEGER NOT NULL REFERENCES users(id)"))
        assertTrue(migration.contains("owner_user_id INTEGER REFERENCES users(id)"))
        assertFalse(migration.contains("raw_token"))
        assertFalse(migration.contains("enrollment_code VARCHAR"))
    }
}
