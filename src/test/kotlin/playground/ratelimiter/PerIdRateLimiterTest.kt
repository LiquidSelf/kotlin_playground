package playground.ratelimiter

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import playground.ratelimit.RateLimitExceededException
import playground.ratelimit.impl.PerIdRateLimiter
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@OptIn(ExperimentalCoroutinesApi::class)
class PerIdRateLimiterTest {

    @Test
    fun `should rate limit per id`() {
        runTest {
            val rateLimiter = PerIdRateLimiter(
                requestsPerWindow = 10,
                windowLength = 1.seconds.toJavaDuration(),
                timeSource = { Instant.ofEpochMilli(currentTime) }
            )

            val id1 = "user1"
            val id2 = "user2"

            val executed1 = AtomicInteger(0)
            val executed2 = AtomicInteger(0)
            val failed1 = AtomicInteger(0)

            // Launch 15 requests for id1
            (1..15).forEach { i ->
                launch {
                    runCatching {
                        rateLimiter.execute(id1) {
                            executed1.incrementAndGet()
                        }
                    }.onFailure {
                        if (it is RateLimitExceededException) {
                            failed1.incrementAndGet()
                        }
                    }
                }
            }

            // Launch 5 requests for id2
            (1..5).forEach { i ->
                launch {
                    runCatching {
                        rateLimiter.execute(id2) {
                            executed2.incrementAndGet()
                        }
                    }
                }
            }

            advanceUntilIdle()

            assertEquals(10, executed1.get(), "Should allow only 10 requests for id1")
            assertEquals(5, failed1.get(), "Should fail 5 requests for id1")
            assertEquals(5, executed2.get(), "Should allow all 5 requests for id2")
        }

    }

    @Test
    fun `window should slide`() {
        runTest {
            val requestsPerWindow = 5
            val windowLength = 1.seconds
            val rateLimiter = PerIdRateLimiter(
                requestsPerWindow = requestsPerWindow,
                windowLength = windowLength.toJavaDuration(),
                timeSource = { Instant.ofEpochMilli(currentTime) }
            )

            val id = "test-id"
            val executed = AtomicInteger(0)

            // First burst, fill the window
            (1..requestsPerWindow).forEach { i ->
                launch {
                    runCatching { rateLimiter.execute(id) { executed.incrementAndGet() } }
                }
            }
            advanceUntilIdle()
            assertEquals(requestsPerWindow, executed.get())

            // Try one more, should fail
            assertFailsWith<RateLimitExceededException> {
                rateLimiter.execute(id) { executed.incrementAndGet() }
            }

            advanceUntilIdle()
            assertEquals(requestsPerWindow, executed.get())

            // Wait for the window to slide
            delay(windowLength)

            // Second burst, should be allowed
            (1..requestsPerWindow).forEach { i ->
                launch {
                    runCatching { rateLimiter.execute(id) { executed.incrementAndGet() } }
                }
            }
            advanceUntilIdle()
            assertEquals(requestsPerWindow * 2, executed.get())
        }
    }
}
