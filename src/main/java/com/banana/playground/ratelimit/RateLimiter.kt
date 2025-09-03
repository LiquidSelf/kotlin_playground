package com.banana.playground.ratelimit

interface RateLimiter {
    suspend fun <T> execute(block: suspend () -> T): T
}