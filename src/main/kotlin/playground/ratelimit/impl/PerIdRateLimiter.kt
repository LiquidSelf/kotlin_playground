package playground.ratelimit.impl

import playground.ratelimit.PerIdRateLimiter
import playground.ratelimit.RateLimitExceededException
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/**
 * A rate limiter implementation that enforces a limit on the number of requests per ID within a sliding window.
 *
 * This implementation is thread-safe.
 *
 * @param requestsPerWindow The maximum number of requests allowed within the window.
 * @param windowLength The duration of the sliding window.
 * @param timeSource A function that returns the current time as an Instant. Defaults to Instant.now().
 */
class PerIdRateLimiter(
    val requestsPerWindow: Int,
    val windowLength: Duration,
    private val timeSource: () -> Instant = Instant::now
) : PerIdRateLimiter {
    //TODO - change into some cache with TTL (to avoid dead weight keys/memory leak)
    private val storage: MutableMap<Any, Deque<Instant>> = ConcurrentHashMap<Any, Deque<Instant>>()

    /**
     * Executes the given block of code if the rate limit for the specified ID is not exceeded.
     *
     * @param id The identifier for which to apply the rate limit.
     * @param block The suspendable block of code to execute.
     * @return The result of the block execution.
     * @throws RateLimitExceededException if the number of requests exceeds the configured limit.
     */
    override suspend fun <T> execute(id: Any, block: suspend () -> T): T {
        val deque = storage.computeIfAbsent(id) { ArrayDeque() }

        val allowed = synchronized(deque) {
            val now = timeSource()
            val windowStart = now.minus(windowLength)

            while (deque.peekFirst()?.isAfter(windowStart) == false) {
                deque.pollFirst()
            }

            if (deque.size < requestsPerWindow) {
                deque.addLast(now)
                true
            } else {
                false
            }
        }

        if (allowed) {
            return block()
        } else {
            throw RateLimitExceededException()
        }
    }
}
