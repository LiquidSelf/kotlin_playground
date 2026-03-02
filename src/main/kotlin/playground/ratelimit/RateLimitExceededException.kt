package playground.ratelimit

class RateLimitExceededException(message: String? = "Rate limit exceeded") : RuntimeException(message)
