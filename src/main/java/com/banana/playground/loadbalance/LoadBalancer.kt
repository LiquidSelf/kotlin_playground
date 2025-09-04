package com.banana.playground.loadbalance

@JvmInline
value class LoadBalancerServer(val address: String)

interface LoadBalancer {
    fun addServer(server: LoadBalancerServer)
    fun removeServer(server: LoadBalancerServer)
    fun getServers(): Collection<LoadBalancerServer>
    fun selectServer(block: (LoadBalancerServer) -> Unit)
}