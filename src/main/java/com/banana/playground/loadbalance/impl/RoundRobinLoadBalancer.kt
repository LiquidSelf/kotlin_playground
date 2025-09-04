package com.banana.playground.loadbalance.impl

import com.banana.playground.loadbalance.LoadBalancer
import com.banana.playground.loadbalance.LoadBalancerServer
import java.util.List.copyOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RoundRobinLoadBalancer : LoadBalancer {

    private val servers = CopyOnWriteArrayList<LoadBalancerServer>()
    private val roundRobinCounter: AtomicInteger = AtomicInteger()

    override fun addServer(server: LoadBalancerServer) {
        servers.addIfAbsent(server)
    }

    override fun removeServer(server: LoadBalancerServer) {
        servers.remove(server)
    }

    override fun getServers(): Collection<LoadBalancerServer> {
        return copyOf(servers)
    }

    override fun selectServer(block: (LoadBalancerServer) -> Unit) {
        check(servers.isNotEmpty()) { "Server list is empty" }
        val pointer = roundRobinCounter.getAndUpdate { if (it == Integer.MAX_VALUE) 0 else it + 1 }
        servers[pointer % servers.size].run(block)
    }
}