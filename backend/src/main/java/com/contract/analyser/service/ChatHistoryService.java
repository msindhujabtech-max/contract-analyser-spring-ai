package com.contract.analyser.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * ChatHistoryService: Persists chat conversations in Redis.
 *
 * Purpose:
 * - Stores chat messages (user questions + AI answers) per contract/user
 * - Survives browser refresh (unlike frontend-only state)
 * - Enables conversation continuity across sessions
 * - Auto-expires after 24 hours to manage memory
 *
 * Redis data structure used: LIST (ordered collection)
 * - Each message is a JSON string pushed to the list
 * - Newest messages at the end (RPUSH)
 * - Retrieve all messages with LRANGE 0 -1
 */
@Service
public class ChatHistoryService {

    private static final String HISTORY_PREFIX = "chat:history:";
    private static final int MAX_MESSAGES = 50;  // Keep last 50 messages per conversation
    private static final Duration HISTORY_TTL = Duration.ofHours(24);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatHistoryService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Save a message (question or answer) to chat history.
     */
    public Mono<Long> saveMessage(Long contractId, Long userId, String role, String content) {
        String key = HISTORY_PREFIX + contractId + ":" + userId;
        Map<String, String> message = Map.of("role", role, "content", content);

        try {
            String json = objectMapper.writeValueAsString(message);
            return redisTemplate.opsForList().rightPush(key, json)
                    .flatMap(size -> {
                        // Trim to keep only the last MAX_MESSAGES
                        if (size > MAX_MESSAGES) {
                            return redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1)
                                    .thenReturn(size);
                        }
                        return redisTemplate.expire(key, HISTORY_TTL).thenReturn(size);
                    });
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    /**
     * Retrieve full chat history for a contract/user pair.
     */
    public Mono<List<String>> getHistory(Long contractId, Long userId) {
        String key = HISTORY_PREFIX + contractId + ":" + userId;
        return redisTemplate.opsForList().range(key, 0, -1)
                .collectList();
    }

    /**
     * Clear chat history (e.g., when a new document is uploaded).
     */
    public Mono<Boolean> clearHistory(Long contractId, Long userId) {
        String key = HISTORY_PREFIX + contractId + ":" + userId;
        return redisTemplate.delete(key).map(count -> count > 0);
    }
}
