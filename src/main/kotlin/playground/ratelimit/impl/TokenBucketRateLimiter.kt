package playground.ratelimit.impl

import playground.ratelimit.RateLimitExceededException
import playground.ratelimit.RateLimiter
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

/**
 * Simple Token Bucket algorithm implementation,
 * bucket being refilled on successful [tryAcquire] call, as a side effect.
 * In case of failed acquire attempt (bucket empty) throws [RateLimitExceededException] immediately.
 * Otherwise, executes block.
 */
class TokenBucketRateLimiter(
    val initialCapacity: Int,
    val maxCapacity: Int,
    val refillPerSecond: Int,
    private val nowProvider: () -> Long = { System.nanoTime() }
) : RateLimiter {

    private val state: AtomicReference<Bucket> =
        AtomicReference(Bucket(initialCapacity.toDouble(), nowProvider()))

    override suspend fun <T> execute(block: suspend () -> T): T {
        if (tryAcquire()) return block()
        else throw RateLimitExceededException()
    }

    fun tryAcquire(): Boolean {
        val now = nowProvider()
        while (true) {
            val old = state.get()
            val elapsedSec = (now - old.lastUpdate).toDouble() / 1.seconds.inWholeNanoseconds
            val tokens = min(maxCapacity.toDouble(), old.tokens + elapsedSec * refillPerSecond)

            if (tokens < 1.0) return false

            val newBucket = Bucket(tokens - 1.0, now)
            if (state.compareAndSet(old, newBucket)) return true
        }
    }

    data class Bucket(val tokens: Double, val lastUpdate: Long)
}