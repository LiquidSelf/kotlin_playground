package com.banana.playground.loadbalance.impl

import com.banana.playground.loadbalance.LoadBalancer
import java.util.Set.copyOf
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class RandomLoadBalancer : LoadBalancer {
    private val servers = CopyOnWriteArrayList<String>()

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
        servers.random(Random)?.block()
    }
}
