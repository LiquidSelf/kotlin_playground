package com.banana.playground.loadbalance.impl

import com.banana.playground.loadbalance.LoadBalancer
import com.banana.playground.loadbalance.LoadBalancerServer
import java.util.Set.copyOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LeastConnectionsLoadBalancer : LoadBalancer {

    private val servers = ConcurrentHashMap<LoadBalancerServer, AtomicInteger>()

    override fun addServer(server: LoadBalancerServer) {
        servers.computeIfAbsent(server) { AtomicInteger(0) }
    }

    override fun removeServer(server: LoadBalancerServer) {
        servers.remove(server)
    }

    override fun getServers(): Collection<LoadBalancerServer> {
        return copyOf(servers.keys)
    }

    override fun selectServer(block: (LoadBalancerServer) -> Unit) {
        val selected = pickServer()
        try {
            selected.value.incrementAndGet()
            selected.key.run(block)
        } finally {
            selected.value.decrementAndGet()
        }
    }

    /**Picks server, using 'power of two random choices' algorithm.*/
    private fun pickServer(): Map.Entry<LoadBalancerServer, AtomicInteger> {
        check(servers.isNotEmpty()) { "Server list is empty" }
        return servers.let { servers.entries.toList() }
            .let { entries ->
                if (entries.size == 1) return entries[0]

                val left = entries.random()
                var right: Map.Entry<LoadBalancerServer, AtomicInteger>
                do {
                    right = entries.random()
                } while (left == right)

                if (left.value.get() <= right.value.get()) left else right
            }
    }
}