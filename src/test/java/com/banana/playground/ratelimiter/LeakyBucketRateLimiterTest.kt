package com.banana.playground.ratelimiter

import com.banana.playground.ratelimit.impl.LeakyBucketRateLimiter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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
        val leakyBucket = LeakyBucketRateLimiter(
            bufferCapacity, rate,
            nowInNanoProvider =  { currentTime.milliseconds.inWholeNanoseconds },
            daemonDispatcher = StandardTestDispatcher(testScheduler)
        )

        //results
        val timestamps = ConcurrentLinkedQueue<Long>()
        val rejected = ConcurrentLinkedQueue<Throwable>()
        (1..tasks).forEach { _ ->
            launch {
                runCatching { leakyBucket.execute { currentTime.milliseconds.inWholeNanoseconds } }
                    .onSuccess { timestamps.add(it) }
                    .onFailure { rejected.add(it) }
            }
        }

        advanceUntilIdle()
        leakyBucket.stop()

        assertTrue(timestamps.size + rejected.size == tasks, "All tasks must be executed/failed")
        (tasks > bufferCapacity).takeIf { it }?.let { assertTrue(rejected.isNotEmpty(), "Rejected tasks expected") }
        assertFailsWith<IllegalStateException> { runBlocking { leakyBucket.execute { } } }

        val nanoDeltas = timestamps.zipWithNext { a, b -> b.nanoseconds - a.nanoseconds }
        if (nanoDeltas.isNotEmpty()) {
            nanoDeltas.reduce { a, b -> a + b }.let {
                val avg = it / nanoDeltas.size
                assertTrue(avg >= (rate * 0.99) && avg <= (rate * 1.01), "Avg: $avg, expected ~ $rate")
                println("OK: AvgRate[$avg] ≈ ExpectedRate[$rate] | Executed: ${timestamps.size}, Rejected: ${rejected.size}")
            }
        }
    }
}
