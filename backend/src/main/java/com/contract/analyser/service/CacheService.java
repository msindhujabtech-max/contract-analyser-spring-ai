package com.contract.analyser.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * CacheService: Manages Redis caching for RAG responses.
 *
 * Purpose:
 * - Caches LLM responses to avoid redundant AI calls for repeated questions
 * - Generates cache keys based on contract_id + user_id + question hash
 * - Supports TTL (Time-To-Live) for automatic cache expiration
 */
@Service
public class CacheService {

    private static final String CACHE_PREFIX = "rag:response:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Duration cacheTtl;

    public CacheService(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${app.cache.response-ttl-minutes:60}") int ttlMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * Generate a unique cache key from the query parameters.
     * Uses SHA-256 hash of the question to handle long questions cleanly.
     *
     * Format: "rag:response:{contractId}:{userId}:{questionHash}"
     */
    public String generateCacheKey(Long contractId, Long userId, String question) {
        String questionHash = hashQuestion(question.trim().toLowerCase());
        return CACHE_PREFIX + contractId + ":" + userId + ":" + questionHash;
    }

    /**
     * Retrieve a cached response. Returns Mono.empty() if not found.
     */
    public Mono<String> getCachedResponse(String cacheKey) {
        return redisTemplate.opsForValue().get(cacheKey);
    }

    /**
     * Store a response in Redis with TTL expiration.
     * After TTL expires, Redis automatically deletes the entry.
     */
    public Mono<Boolean> cacheResponse(String cacheKey, String response) {
        return redisTemplate.opsForValue().set(cacheKey, response, cacheTtl);
    }

    /**
     * Invalidate all cached responses for a specific contract.
     * Called when a new document is uploaded (old answers may be stale).
     */
    public Mono<Long> invalidateContractCache(Long contractId) {
        String pattern = CACHE_PREFIX + contractId + ":*";
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) return Mono.just(0L);
                    return redisTemplate.delete(keys.toArray(new String[0]));
                });
    }

    /**
     * SHA-256 hash of the question text.
     * Ensures consistent key length regardless of question length.
     */
    private String hashQuestion(String question) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(question.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16); // First 16 chars is enough
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new RuntimeException(e);
        }
    }
}
