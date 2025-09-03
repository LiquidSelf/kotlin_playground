package com.banana.playground.loadbalance

enum class LoadBalancingStrategy(val description: String) {
    /**
     * Selects servers in a round‑robin fashion.
     */
    ROUND_ROBIN("Round Robin"),

    /**
     * Chooses a server at random from the pool.
     */
    RANDOM("Random Selection"),

    /**
     * Picks the server with the fewest active connections (requires additional state).
     */
    LEAST_CONNECTIONS("Least Connections")
}