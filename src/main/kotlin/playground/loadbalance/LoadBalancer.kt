package playground.loadbalance

interface LoadBalancer {
    fun addServer(server: ServerAddress)
    fun removeServer(server: ServerAddress)
    fun getServers(): Collection<ServerAddress>
    fun selectServer(block: (ServerAddress) -> Unit)

    @JvmInline
    value class ServerAddress(val address: String)
}