package com.banana.playground.loadbalancer

import com.banana.playground.loadbalance.DelegatingLoadBalancer
import com.banana.playground.loadbalance.LoadBalancerServer
import com.banana.playground.loadbalance.LoadBalancingStrategy
import com.banana.playground.loadbalance.impl.LeastConnectionsLoadBalancer
import com.banana.playground.loadbalance.impl.RandomLoadBalancer
import com.banana.playground.loadbalance.impl.RoundRobinLoadBalancer
import kotlinx.coroutines.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class LoadBalancerTest {

    @ParameterizedTest
    @EnumSource(LoadBalancingStrategy::class)
    fun testAddServer(strategy: LoadBalancingStrategy) {
        val loadBalancer = DelegatingLoadBalancer(strategy)

        val testServer = LoadBalancerServer("testServer")
        assertThat("Expected no servers", loadBalancer.getServers(), empty())
        loadBalancer.addServer(testServer)
        assertThat("Contains server", loadBalancer.getServers(), contains(testServer))
        assertThat("Should contain only one server", loadBalancer.getServers().size, equalTo(1))
    }


    @ParameterizedTest
    @EnumSource(LoadBalancingStrategy::class)
    fun testRemoveServer(strategy: LoadBalancingStrategy) {
        val loadBalancer = DelegatingLoadBalancer(strategy)

        val testServer = LoadBalancerServer("testServer")
        assertThat("Expected no servers", loadBalancer.getServers(), empty())
        loadBalancer.addServer(testServer)
        assertThat("Should contain one server", loadBalancer.getServers().size, equalTo(1))
        loadBalancer.removeServer(testServer)
        assertThat("Server must be removed", loadBalancer.getServers(), empty())
    }

    @ParameterizedTest
    @EnumSource(LoadBalancingStrategy::class)
    fun testDelegation(strategy: LoadBalancingStrategy) = runBlocking {
        val loadBalancer = DelegatingLoadBalancer(strategy)

        //hm...
        val delegate = loadBalancer.javaClass.declaredFields
            .first { it.name.contains("delegate") }
            .let {
                it.trySetAccessible()
                it.get(loadBalancer)
            }

        when (strategy) {
            LoadBalancingStrategy.ROUND_ROBIN -> assertInstanceOf<RoundRobinLoadBalancer>(
                delegate,
                "Should be ${strategy.description}"
            )

            LoadBalancingStrategy.RANDOM -> assertInstanceOf<RandomLoadBalancer>(
                delegate,
                "Should be ${strategy.description}"
            )

            LoadBalancingStrategy.LEAST_CONNECTIONS -> assertInstanceOf<LeastConnectionsLoadBalancer>(
                delegate,
                "Should be ${strategy.description}"
            )
        }
        Unit
    }

    @ParameterizedTest
    @EnumSource(LoadBalancingStrategy::class)
    fun testSelectServer(strategy: LoadBalancingStrategy) = runBlocking {
        val loadBalancer = DelegatingLoadBalancer(strategy)

        assertThat("0 servers expected", loadBalancer.getServers(), empty())
        assertFailsWith<IllegalStateException> { loadBalancer.selectServer { } }

        val serverCount = 100

        val addedServers = mutableListOf<LoadBalancerServer>()
        repeat(serverCount) { i ->
            LoadBalancerServer("testServer_$i").also { server ->
                addedServers.add(server)
                loadBalancer.addServer(server)
                assertContains(loadBalancer.getServers(), server, "Should contain server")
            }
            loadBalancer.selectServer {
                assertContains(addedServers, it, "Should select on of added servers")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(LoadBalancingStrategy::class)
    fun testSelectServer_distribution(strategy: LoadBalancingStrategy) = runBlocking {
        val loadBalancer = DelegatingLoadBalancer(strategy)
        val startLatch = CountDownLatch(1)

        assertThat("Expected 0 servers", loadBalancer.getServers(), empty())

        val serverCount = 100
        val requestCount = 100_000
        repeat(serverCount) { i ->
            LoadBalancerServer("testServer_$i").also { server ->
                loadBalancer.addServer(server)
                assertThat("Should contain server", loadBalancer.getServers(), hasItem(server))
            }
        }

        assertEquals(serverCount, loadBalancer.getServers().size, "Should contain $serverCount servers")

        val serverAndDistribution = ConcurrentHashMap<LoadBalancerServer, AtomicInteger>()
        val awaitUs: MutableList<Deferred<Any>> = mutableListOf()
        val executedCount = AtomicInteger(0)
        repeat(requestCount) {
            awaitUs += async(Dispatchers.Default) {
                startLatch.await()
                loadBalancer.selectServer {
                    executedCount.incrementAndGet()
                    serverAndDistribution.compute(it) { _, value ->
                        (value ?: AtomicInteger(0)).apply { incrementAndGet() }
                    }
                }
            }
        }
        startLatch.countDown()
        awaitAll(*awaitUs.toTypedArray())

        assertThat(
            "Expected all requests to be distributed",
            executedCount.get(),
            allOf(
                equalTo(serverAndDistribution.values.sumOf { it.get() }),
                equalTo(requestCount)
            )
        )

        val expectedAvg = requestCount / serverCount.toDouble()
        serverAndDistribution.values.forEach { count ->
            assertThat(
                "${strategy.description} distribution out of bounds",
                count.toDouble(),
                allOf(greaterThanOrEqualTo(expectedAvg * 0.8), lessThanOrEqualTo(expectedAvg * 1.2))
            )
        }
        println("OK: Expected Distribution ≈ ${expectedAvg.toLong()} | ${strategy.description} distributions:\n${serverAndDistribution.values}")
    }
}