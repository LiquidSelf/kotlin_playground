package playground.ratelimiter

import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import playground.ratelimit.impl.LeakyBucketRateLimiter
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class LeakyBucketRateLimiterTest {

    @ParameterizedTest
    @CsvSource(
        "50, 50, 50",
        "0.01, 20000, 10000",
        "0.001, 100000, 100000",
        "5, 500, 100",
        "1, 2000, 2000",
        "50, 20, 20",
        "20, 50, 50",
        "2, 1000, 100",
        "1000, 50000, 25000",
        "2000, 2000, 2000",
    )
    @OptIn(ExperimentalCoroutinesApi::class)
    fun leakyBucket_Sanity_Test(rateMs: Double, tasks: Int, bufferCapacity: Int) = runTest {
        val rate = rateMs.milliseconds

        val bucketScope = CoroutineScope(StandardTestDispatcher(testScheduler) + CoroutineName("BucketCoroutine"))
        val clientScope = CoroutineScope(StandardTestDispatcher(testScheduler) + CoroutineName("ClientCoroutine"))

        val leakyBucket = LeakyBucketRateLimiter(
            bufferCapacity, rate,
            nowInNanoProvider = { currentTime.milliseconds.inWholeNanoseconds },
            dispatcherScope = bucketScope
        )

        //results
        val timestamps = ConcurrentLinkedQueue<Long>()
        val failed = ConcurrentLinkedQueue<Throwable>()

        (1..tasks).forEach { _ ->
            launch(clientScope.coroutineContext) {
                runCatching {
                    leakyBucket.execute {
                        assertEquals(
                            this.coroutineContext[CoroutineName]?.name,
                            clientScope.coroutineContext[CoroutineName]?.name
                        )
                        assertNotEquals(
                            this.coroutineContext[CoroutineName]?.name,
                            bucketScope.coroutineContext[CoroutineName]?.name
                        )
                        currentTime.milliseconds.inWholeNanoseconds
                    }
                }
                    .onSuccess { timestamps.add(it) }
                    .onFailure { failed.add(it) }
            }
        }

        advanceUntilIdle()
        leakyBucket.stop()

        assertTrue(timestamps.size + failed.size == tasks, "All tasks must be executed/failed")
        (tasks > bufferCapacity).takeIf { it }?.let { assertTrue(failed.isNotEmpty(), "Rejected tasks expected") }
        assertFailsWith<IllegalStateException> { runBlocking { leakyBucket.execute { } } }

        val nanoDeltas = timestamps.zipWithNext { a, b -> b.nanoseconds - a.nanoseconds }
        if (nanoDeltas.isNotEmpty()) {
            nanoDeltas.reduce { a, b -> a + b }.let {
                val avg = it / nanoDeltas.size
                assertTrue(avg >= (rate * 0.99) && avg <= (rate * 1.01), "Avg: $avg, expected ~ $rate")
                println("OK: AvgRate[$avg] ≈ ExpectedRate[$rate] | Executed: ${timestamps.size}, Rejected: ${failed.size}")
            }
        } else {
            fail("Failed: AvgRate[Unknown?]. ExpectedRate[$rate] | Executed: ${timestamps.size}, Rejected: ${failed.size}")
        }
    }
}
