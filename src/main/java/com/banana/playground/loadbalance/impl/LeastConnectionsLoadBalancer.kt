package com.banana.playground.loadbalance.impl

import com.banana.playground.loadbalance.LoadBalancer
import java.util.Set.copyOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LeastConnectionsLoadBalancer : LoadBalancer {

    private val servers = ConcurrentHashMap<String, AtomicInteger>()

    override fun addServer(server: String) {
        servers.computeIfAbsent(server) { AtomicInteger(0) }
    }

    override fun removeServer(server: String) {
        servers.remove(server)
    }

    override fun getServers(): Set<String> {
        return copyOf(servers.keys)
    }

    override fun selectServer(block: String.() -> Unit) {
        if (servers.isEmpty()) return

        val selected = pickServer()
        try {
            selected.value.incrementAndGet()
            selected.key.block()
        } finally {
            selected.value.decrementAndGet()
        }
    }

    /**Picks server, using 'power of two random choices' algorithm.*/
    private fun pickServer(): Map.Entry<String, AtomicInteger> {
        val entries = servers.entries.toList()
        if (entries.size == 1) return entries[0]
        val a = entries.random()
        var b: Map.Entry<String, AtomicInteger>
        do {
            b = entries.random()
        } while (a == b)

        return if (a.value.get() <= b.value.get()) a else b
    }
}