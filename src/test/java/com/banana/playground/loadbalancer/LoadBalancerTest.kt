package com.banana.playground.loadbalancer

import com.banana.playground.loadbalance.DelegatingLoadBalancer
import com.banana.playground.loadbalance.LoadBalancingStrategy
import kotlinx.coroutines.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class LoadBalancerTest {

    @ParameterizedTest
    @EnumSource(LoadBalancingStrategy::class)
    fun testAddServer(strategy: LoadBalancingStrategy) {
        val loadBalancer = DelegatingLoadBalancer(strategy)

        val testServer = "testServer"
        assertThat("Expected no servers", loadBalancer.getServers(), empty())
        loadBalancer.addServer(testServer)
        assertThat("Contains server", loadBalancer.getServers(), contains(testServer))
        assertThat("Should contain only one server", loadBalancer.getServers().size, equalTo(1))
    }


    @ParameterizedTest
    @EnumSource(LoadBalancingStrategy::class)
    fun testRemoveServer(strategy: LoadBalancingStrategy) {
        val loadBalancer = DelegatingLoadBalancer(strategy)

        val testServer = "testServer"
        assertThat("Expected no servers", loadBalancer.getServers(), empty())
        loadBalancer.addServer(testServer)
        assertThat("Should contain one server", loadBalancer.getServers().size, equalTo(1))
        loadBalancer.removeServer(testServer)
        assertThat("Server must be removed", loadBalancer.getServers(), empty())
    }

    @ParameterizedTest
    @EnumSource(LoadBalancingStrategy::class)
    fun testSelectServer(strategy: LoadBalancingStrategy) = runBlocking {
        val loadBalancer = DelegatingLoadBalancer(strategy)
        val startLatch = CountDownLatch(1)

        val serverCount = 100
        val requestCount = 500_000

        repeat(serverCount) { i ->
            val serverName = "testServer_$i"
            loadBalancer.addServer(serverName)
            assertThat("Should contain server", loadBalancer.getServers(), hasItem(serverName))
        }

        val map: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap<String, AtomicInteger>()
        val awaitUs: MutableList<Deferred<Any>> = mutableListOf()
        repeat(requestCount) {
            awaitUs += async(Dispatchers.Default) {
                startLatch.await()
                loadBalancer.selectServer {
                    map.compute(this) { _, value -> (value ?: AtomicInteger(1)).apply { incrementAndGet() } }
                }
            }
        }
        startLatch.countDown()
        awaitAll(*awaitUs.toTypedArray())

        val expectedAvg = requestCount / serverCount.toDouble()
        val lowerBound = expectedAvg * 0.8
        val upperBound = expectedAvg * 1.2

        map.values.forEach { count ->
            assertThat(
                "${strategy.description} distribution out of bounds",
                count.toDouble(),
                both(greaterThanOrEqualTo(lowerBound))
                    .and(lessThanOrEqualTo(upperBound))
            )
        }
        println("OK: Expected Distribution ≈ ${expectedAvg.toLong()} | ${strategy.description} distributions:\n${map.values}")
    }
}