package playground.loadbalance.impl

import playground.loadbalance.LoadBalancer
import playground.loadbalance.LoadBalancer.ServerAddress
import java.util.List.copyOf
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class RandomLoadBalancer : LoadBalancer {

    private val servers = CopyOnWriteArrayList<ServerAddress>()

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
        servers.random(Random).run(block)
    }
}
