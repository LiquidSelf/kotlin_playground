package com.banana.playground.loadbalance

import com.banana.playground.loadbalance.impl.LeastConnectionsLoadBalancer
import com.banana.playground.loadbalance.impl.RandomLoadBalancer
import com.banana.playground.loadbalance.impl.RoundRobinLoadBalancer

/**
 * Delegates all LoadBalancer operations to the concrete strategy implementation.
 */
class DelegatingLoadBalancer(strategy: LoadBalancingStrategy) : LoadBalancer by createBalancer(strategy) {
    companion object Factory {
        private fun createBalancer(strategy: LoadBalancingStrategy): LoadBalancer {
            return when (strategy) {
                LoadBalancingStrategy.RANDOM -> RandomLoadBalancer()
                LoadBalancingStrategy.ROUND_ROBIN -> RoundRobinLoadBalancer()
                LoadBalancingStrategy.LEAST_CONNECTIONS -> LeastConnectionsLoadBalancer()
            }
        }
    }
}