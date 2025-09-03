package com.banana.playground.ratelimit.impl

import com.banana.playground.ratelimit.RateLimiter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A leaky‑bucket rate limiter that processes requests sequentially with a configurable delay.
 *
 * <p>The limiter accepts up to {@code bufferCapacity} pending requests. Each request is wrapped in a
 * {@link CompletableDeferred} and queued; the internal coroutine drains the queue, ensuring that
 * at most one request is executed per {@code delay}. The implementation uses a dedicated daemon
 * dispatcher so it does not block application threads.
 *
 * @param bufferCapacity maximum number of pending requests (default: {@link Integer#MAX_VALUE})
 * @param delay          time interval between consecutive executions
 * @param nowInNanoProvider provides the current time in nanoseconds; defaults to {@code System.nanoTime()}
 * @param daemonDispatcher dispatcher used for the internal coroutine; defaults to a single‑threaded daemon executor
 */
class LeakyBucketRateLimiter(
    val bufferCapacity: Int = Int.MAX_VALUE,
    val delay: Duration,
    private val nowInNanoProvider: () -> Long = { System.nanoTime() },
    private val daemonDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable).apply {
                isDaemon = true
                name = "LeakyBucketRL"
            }
        }.asCoroutineDispatcher()
) : RateLimiter {
    private val requestQueue = Channel<CompletableDeferred<Unit>>(bufferCapacity)
    private val privateScope = CoroutineScope(daemonDispatcher + CoroutineName("LeakyBucket"))

    init {
        privateScope.launch {
            start()
        }
    }

    /**
     * Executes the given block under rate‑limiting constraints.
     *
     * @param block the operation to run after some delay
     * @return the result of {@code block}
     * @throws IllegalStateException if the limiter's buffer is full
     */
    override suspend fun <T> execute(block: suspend () -> T): T {
        val deferred = CompletableDeferred<Unit>()

        val wrapper: suspend () -> T = {
            deferred.await()
            block()
        }

        val trySend = requestQueue.trySend(deferred)
        if (trySend.isSuccess) return wrapper()
        else throw trySend.exceptionOrNull() ?: IllegalStateException("Rate limiter overflown")
    }


    /**Drain [requestQueue], with best attempt to compensate the delay if necessary (the JVM may not always wake up threads promptly, especially at low delays)*/
    private suspend fun start() {
        val stepNs = delay
        var oversleepNs = 0.nanoseconds

        for (deferred in requestQueue) {
            val elapsed = measureNanoTime {
                deferred.complete(Unit)
                if (oversleepNs > stepNs) oversleepNs -= stepNs
                else delay(delay)
            }
            if (elapsed > stepNs) oversleepNs += (elapsed - stepNs)
        }
    }

    /**Executes the given [block] and returns elapsed time in nanoseconds.*/
    private inline fun measureNanoTime(block: () -> Unit): Duration {
        val start = nowInNanoProvider()
        block()
        return (nowInNanoProvider() - start).nanoseconds
    }

    /**Stops this Rate Limiter, draining remaining tasks.*/
    suspend fun stop() {
        val message = "Rate limiter stopped"
        requestQueue.close()
        for (deferred in requestQueue) {
            deferred.cancel(message)
        }
        privateScope.cancel(message)
        daemonDispatcher.cancel(CancellationException(message))
    }
}