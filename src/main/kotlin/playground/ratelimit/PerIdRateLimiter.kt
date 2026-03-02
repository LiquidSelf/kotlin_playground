package playground.ratelimit

/**
 * Ratelimiter as suspendable 'wrapper' around executable (limited) block.
 */
interface PerIdRateLimiter {

    /**
     * Executes [block] within some rate.
     */
    suspend fun <T> execute(id: Any, block: suspend () -> T): T
}