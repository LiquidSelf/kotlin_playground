package com.banana.playground.loadbalance.impl

import com.banana.playground.loadbalance.LoadBalancer
import com.banana.playground.loadbalance.LoadBalancerServer
import java.util.List.copyOf
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class RandomLoadBalancer : LoadBalancer {

    private val servers = CopyOnWriteArrayList<LoadBalancerServer>()

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
        servers.random(Random).run(block)
    }
}
