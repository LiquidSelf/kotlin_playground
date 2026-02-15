package playground.loadbalance.impl

import playground.loadbalance.LoadBalancer
import playground.loadbalance.LoadBalancer.ServerAddress
import java.util.List.copyOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RoundRobinLoadBalancer : LoadBalancer {

    private val servers = CopyOnWriteArrayList<ServerAddress>()
    private val roundRobinCounter: AtomicInteger = AtomicInteger()

    override fun addServer(server: ServerAddress) {
        servers.addIfAbsent(server)
    }

    override fun removeServer(server: ServerAddress) {
        servers.remove(server)
    }

    override fun getServers(): Collection<ServerAddress> {
        return copyOf(servers)
    }

    override fun selectServer(block: (ServerAddress) -> Unit) {
        check(servers.isNotEmpty()) { "Server list is empty" }
        val pointer = roundRobinCounter.getAndUpdate { if (it == Integer.MAX_VALUE) 0 else it + 1 }
        servers[pointer % servers.size].run(block)
    }
}