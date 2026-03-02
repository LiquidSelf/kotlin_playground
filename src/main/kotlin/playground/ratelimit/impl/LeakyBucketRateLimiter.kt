package playground.ratelimit.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import playground.ratelimit.RateLimitExceededException
import playground.ratelimit.RateLimiter
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A leaky‑bucket rate limiter that enqueues executables and execute them with [delay] interval, sequentially.
 * [dispatcherScope] only 'releases' task, task itself will get executed on an original dispatcher.
 *
 * <p>The limiter accepts up to {@code bufferCapacity} pending requests. Each request is wrapped in a
 * {@link CompletableDeferred} and queued; the internal coroutine drains the queue, ensuring that
 * at most one request is executed per {@code delay}. The implementation uses a dedicated daemon
 * dispatcher so it does not block application threads.
 *
 * @param bufferCapacity maximum number of pending requests (default: {@link Integer#MAX_VALUE})
 * @param delay          time interval between consecutive executions
 * @param nowInNanoProvider provides the current time in nanoseconds; defaults to {@code System.nanoTime()}
 * @param dispatcherScope dispatcher used for the internal tasks 'release' process; defaults to a single‑threaded daemon executor
 */
class LeakyBucketRateLimiter(
    val bufferCapacity: Int = Int.MAX_VALUE,
    val delay: Duration,
    private val nowInNanoProvider: () -> Long = { System.nanoTime() },
    private val dispatcherScope: CoroutineScope =
        CoroutineScope(
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable).apply {
                    isDaemon = true
                    name = "LeakyBucketRL_Thread"
                }
            }.asCoroutineDispatcher() + CoroutineName("RL_Scope")
        ),
) : RateLimiter {
    private val requestQueue = Channel<CompletableDeferred<Unit>>(bufferCapacity)

    init {
        dispatcherScope.launch {
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
        else throw trySend.exceptionOrNull() ?: RateLimitExceededException("Rate limiter overflown")
    }


    /**Drain [requestQueue], with best attempt to compensate the delay if necessary (the JVM may not always wake up threads promptly, especially at low delays)*/
    private suspend fun start() {
        var oversleepNs = Duration.ZERO
        for (deferred in requestQueue) {
            val elapsed = measureNanoTime {
                deferred.complete(Unit)
                if (oversleepNs > delay) oversleepNs -= delay
                else delay(delay)
            }
            if (elapsed > delay) oversleepNs += (elapsed - delay)
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
        dispatcherScope.cancel(message)
    }
}