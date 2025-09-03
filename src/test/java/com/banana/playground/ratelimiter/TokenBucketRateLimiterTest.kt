package com.banana.playground.ratelimiter

import com.banana.playground.ratelimit.impl.TokenBucketRateLimiter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Stream
import kotlin.math.floor
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenBucketRateLimiterTest {

    @ParameterizedTest
    @MethodSource("tokenBucket_paramsProvider")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tokenBucket_Sanity_Test(delayPerTaskMs: Long, initialCapacity: Int, threads: Int, refillPerSecond: Int) =
        runTest {
            val rateLimiter = TokenBucketRateLimiter(
                initialCapacity,
                Integer.MAX_VALUE,
                refillPerSecond
            ) { currentTime.milliseconds.inWholeNanoseconds }


            val executed = AtomicLong(0L)
            (1..threads).forEach { _ ->
                launch {
                    delay(delayPerTaskMs.milliseconds)
                    runCatching { rateLimiter.execute { executed.incrementAndGet() } }
                }
            }
            advanceUntilIdle()

            val expectedThroughPut =
                min(
                    floor(currentTime.milliseconds.inWholeNanoseconds / 1.seconds.inWholeNanoseconds.toDouble() * refillPerSecond) + initialCapacity,
                    threads.toDouble()
                )

            MatcherAssert.assertThat(
                "Limit must be in bound",
                executed.get().toDouble(),
                Matchers.equalTo(expectedThroughPut)
            )

            println("OK: [expected ${expectedThroughPut.toLong()} | acquired = $executed] - runTime = ${currentTime.milliseconds} | delay=${delayPerTaskMs.milliseconds}, threads = $threads, initialCapacity=${rateLimiter.initialCapacity} , refillPerSecond = ${rateLimiter.refillPerSecond}")
        }

    fun tokenBucket_paramsProvider(): Stream<Arguments> {
        // Predefined delays
        val delays = listOf(
            1.milliseconds.inWholeMilliseconds,
            20.milliseconds.inWholeMilliseconds,
            100.milliseconds.inWholeMilliseconds,
            500.milliseconds.inWholeMilliseconds,
            1.seconds.inWholeMilliseconds,
        )
        val threadCounts = listOf(1, 1_000, 5_000, 10_000, 20_000, 50_000, 100_000)
        val refillRates = listOf(1, 10, 500, 1_000)

        return Stream.generate {
            val delay = delays.random()
            val threads = threadCounts.random()
            val refillPerSecond = refillRates.random()

            val maxInitial = (threads / 5 - 1).coerceAtLeast(1)
            val initialCapacity = (0..maxInitial).random()

            Arguments.of(delay, initialCapacity, threads, refillPerSecond)
        }.limit(20) // produce exactly 10 iterations
    }
}