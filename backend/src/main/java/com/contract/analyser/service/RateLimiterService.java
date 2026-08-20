package com.contract.analyser.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * RateLimiterService: Prevents API abuse using Redis-based sliding window rate limiting.
 *
 * How it works:
 * - Each user gets a Redis key: "ratelimit:{userId}"
 * - Each request increments the counter
 * - Counter auto-expires after 1 minute (TTL)
 * - If counter exceeds the limit, request is rejected
 *
 * Why Redis for rate limiting?
 * - Atomic increment operations (no race conditions)
 * - Auto-expiry with TTL (no cleanup needed)
 * - Shared across multiple backend instances (works with Kubernetes replicas)
 */
@Service
public class RateLimiterService {

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int maxRequestsPerMinute;

    public RateLimiterService(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${app.cache.rate-limit-requests-per-minute:20}") int maxRequests
    ) {
        this.redisTemplate = redisTemplate;
        this.maxRequestsPerMinute = maxRequests;
    }

    /**
     * Check if the user is within the rate limit.
     * Returns true if allowed, false if limit exceeded.
     */
    public Mono<Boolean> isAllowed(Long userId) {
        String key = RATE_LIMIT_PREFIX + userId;

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request — set expiry to 1 minute
                        return redisTemplate.expire(key, Duration.ofMinutes(1))
                                .thenReturn(true);
                    }
                    // Check if under limit
                    return Mono.just(count <= maxRequestsPerMinute);
                });
    }

    /**
     * Get remaining requests for a user in the current window.
     */
    public Mono<Long> getRemainingRequests(Long userId) {
        String key = RATE_LIMIT_PREFIX + userId;
        return redisTemplate.opsForValue().get(key)
                .map(count -> Math.max(0, maxRequestsPerMinute - Long.parseLong(count)))
                .defaultIfEmpty((long) maxRequestsPerMinute);
    }
}
