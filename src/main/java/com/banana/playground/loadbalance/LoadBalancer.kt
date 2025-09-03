package com.banana.playground.loadbalance

interface LoadBalancer {
    fun addServer(server: String)
    fun removeServer(server: String)
    fun getServers(): Set<String>
    fun selectServer(block: String.() -> Unit)
}