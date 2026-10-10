package backend.security

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

class RateLimiter(private val limit: Int, private val windowMillis: Long, private val clock: Clock = Clock.systemUTC()) {
    private val buckets = ConcurrentHashMap<String, ArrayDeque<Long>>()
    fun allow(key: String): Boolean {
        val now = clock.millis()
        val bucket = buckets.computeIfAbsent(key) { ArrayDeque() }
        synchronized(bucket) {
            while (bucket.isNotEmpty() && now - bucket.first() >= windowMillis) bucket.removeFirst()
            if (bucket.size >= limit) return false
            bucket.addLast(now)
            return true
        }
    }
}
