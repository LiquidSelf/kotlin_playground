package playground.loadbalance.impl

import playground.loadbalance.LoadBalancer
import playground.loadbalance.LoadBalancer.ServerAddress
import java.util.Set.copyOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LeastConnectionsLoadBalancer : LoadBalancer {

    private val servers = ConcurrentHashMap<ServerAddress, AtomicInteger>()

    override fun addServer(server: ServerAddress) {
        servers.computeIfAbsent(server) { AtomicInteger(0) }
    }

    override fun removeServer(server: ServerAddress) {
        servers.remove(server)
    }

    override fun getServers(): Collection<ServerAddress> {
        return copyOf(servers.keys)
    }

    override fun selectServer(block: (ServerAddress) -> Unit) {
        val selected = pickServer()
        try {
            selected.value.incrementAndGet()
            selected.key.run(block)
        } finally {
            selected.value.decrementAndGet()
        }
    }

    /**Picks server, using 'power of two random choices' algorithm.*/
    private fun pickServer(): Map.Entry<ServerAddress, AtomicInteger> {
        check(servers.isNotEmpty()) { "Server list is empty" }
        return servers.let { servers.entries.toList() }
            .let { entries ->
                if (entries.size == 1) return entries[0]

                val left = entries.random()
                var right: Map.Entry<ServerAddress, AtomicInteger>
                do {
                    right = entries.random()
                } while (left == right)

                if (left.value.get() <= right.value.get()) left else right
            }
    }
}