package com.banana.playground.loadbalance.impl

import com.banana.playground.loadbalance.LoadBalancer
import java.util.Set.copyOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RoundRobinLoadBalancer : LoadBalancer {
    private val servers = CopyOnWriteArrayList<String>()
    private val roundRobinCounter: AtomicInteger = AtomicInteger()

    override fun addServer(server: String) {
        servers.addIfAbsent(server)
    }

    override fun removeServer(server: String) {
        servers.remove(server)
    }

    override fun getServers(): Set<String> {
        return copyOf(servers)
    }

    override fun selectServer(block: String.() -> Unit) {
        if (servers.isEmpty()) return
        val pointer = roundRobinCounter.getAndUpdate { if (it >= Integer.MAX_VALUE) 0 else it + 1 }
        servers[pointer % servers.size]?.block()
    }
}