package playground.ratelimit

/**
 * Ratelimiter as suspendable 'wrapper' around executable (limited) block.
 */
interface RateLimiter {

    /**
     * Executes [block] within some rate.
     */
    suspend fun <T> execute(block: suspend () -> T): T

    class RateLimitExceededException(message: String? = "Rate limit exceeded") : RuntimeException(message)
}