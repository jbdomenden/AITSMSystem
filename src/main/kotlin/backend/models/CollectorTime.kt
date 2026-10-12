package backend.models

import java.time.Instant

internal fun normalizeCollectorMeasurement(value: String, serverNow: Instant): Instant {
    val parsed = runCatching { Instant.parse(value) }
        .getOrElse { throw IllegalArgumentException("Measurement timestamp must be ISO-8601 UTC.") }
    return if (parsed.isAfter(serverNow.plusSeconds(300)) || parsed.isBefore(serverNow.minusSeconds(86_400))) serverNow else parsed
}
