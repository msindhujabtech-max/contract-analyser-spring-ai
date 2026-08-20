# Redis Integration — Step-by-Step Implementation Guide
## AI Contract Analyzer Project

---

# TABLE OF CONTENTS

1. [Why Redis in This Project](#1-why-redis-in-this-project)
2. [Architecture Before vs After Redis](#2-architecture-before-vs-after-redis)
3. [Step 1: Add Redis to Docker Compose](#step-1-add-redis-to-docker-compose)
4. [Step 2: Add Maven Dependency](#step-2-add-maven-dependency)
5. [Step 3: Configure Redis in application.yml](#step-3-configure-redis-in-applicationyml)
6. [Step 4: Create RedisConfig.java](#step-4-create-redisconfigjava)
7. [Step 5: Create CacheService.java](#step-5-create-cacheservicejava)
8. [Step 6: Create RateLimiterService.java](#step-6-create-ratelimiterservicejava)
9. [Step 7: Create ChatHistoryService.java](#step-7-create-chathistoryservicejava)
10. [Step 8: Update RagService.java (The Core Change)](#step-8-update-ragservicejava)
11. [Step 9: Update DocumentIngestionService.java](#step-9-update-documentingestionservicejava)
12. [Step 10: Update ChatController.java](#step-10-update-chatcontrollerjava)
13. [Step 11: Kubernetes Redis Manifest](#step-11-kubernetes-redis-manifest)
14. [Complete Data Flow with Redis](#complete-data-flow-with-redis)
15. [Redis Data Structures Used](#redis-data-structures-used)
16. [Interview Q&A](#interview-qa)

---

# 1. Why Redis in This Project

## Problem Without Redis

| Issue | Impact |
|-------|--------|
| Same question asked twice → full LLM call both times | Wasted 5-10 seconds + compute resources |
| No rate limiting → user can spam API | Server overloaded, Ollama crashes |
| Chat history lost on browser refresh | Poor user experience |
| Multiple backend pods can't share state | Inconsistent behavior in Kubernetes |

## What Redis Solves

| Feature | How Redis Helps | Response Time |
|---------|----------------|--------------|
| **Response Caching** | Store LLM answers, return instantly on repeat questions | 5000ms → 2ms |
| **Rate Limiting** | Count requests per user per minute, block if exceeded | N/A (protection) |
| **Chat History** | Persist conversations server-side, survive refresh | N/A (UX improvement) |
| **Cache Invalidation** | Delete stale cache when new document uploaded | Automatic |

## Why Redis Specifically?

| Requirement | Why Redis Fits |
|-------------|---------------|
| Sub-millisecond reads | In-memory storage (RAM, not disk) |
| TTL (auto-expiry) | Built-in `EXPIRE` command — no cleanup jobs needed |
| Atomic operations | `INCR` for rate limiting is thread-safe |
| Shared across pods | All Kubernetes replicas connect to same Redis instance |
| Data structures | Strings (cache), Lists (history), Counters (rate limit) |

---

# 2. Architecture Before vs After Redis

## BEFORE (No Redis)

```
User Question
    ↓
Backend receives request
    ↓
Vector search (PostgreSQL) — 50ms
    ↓
Embed question (Ollama) — 400ms
    ↓
LLM generates answer (Ollama) — 5000-10000ms
    ↓
Return to user
    ↓
TOTAL: 5-10 seconds EVERY TIME (even repeat questions)
```

## AFTER (With Redis)

```
User Question
    ↓
Rate Limit Check (Redis INCR) — 1ms
    ↓ (allowed)
Cache Check (Redis GET) — 1ms
    ↓
┌─── CACHE HIT ───┐     ┌─── CACHE MISS ───────────────────┐
│ Return cached    │     │ Vector search (PostgreSQL) — 50ms │
│ response         │     │ Embed question (Ollama) — 400ms   │
│ TOTAL: 2ms       │     │ LLM generates answer — 5-10s      │
└──────────────────┘     │ Cache response (Redis SET) — 1ms  │
                         │ Save to history (Redis RPUSH) — 1ms│
                         │ TOTAL: 5-10s (first time only)    │
                         └───────────────────────────────────┘
```

---

# Step 1: Add Redis to Docker Compose

**File: `docker-compose.yml`**

```yaml
  redis:
    image: redis:7-alpine
    container_name: contract-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
```

### Line-by-line explanation:

| Line | Purpose |
|------|---------|
| `image: redis:7-alpine` | Redis 7 on Alpine Linux (tiny ~30MB image) |
| `ports: "6379:6379"` | Expose Redis on default port 6379 |
| `volumes: redis_data:/data` | Persist data to Docker volume (survives restart) |
| `command: redis-server --appendonly yes` | Enable AOF persistence (writes every operation to disk) |
| `healthcheck: redis-cli ping` | Docker checks if Redis is responding (returns PONG) |

### Backend dependency added:

```yaml
    depends_on:
      redis:
        condition: service_healthy
```

This ensures the backend starts ONLY after Redis responds to `ping`.

### Environment variables for backend:

```yaml
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
```

- `redis` is the Docker service name — Docker DNS resolves it to the container's IP.

### Volume declaration:

```yaml
volumes:
  redis_data:
```

Named volume for Redis AOF persistence.

---

# Step 2: Add Maven Dependency

**File: `backend/pom.xml`**

```xml
<!-- Spring Data Redis (Reactive) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

### What this provides:

| Component | Purpose |
|-----------|---------|
| `ReactiveRedisConnectionFactory` | Non-blocking connection pool to Redis |
| `ReactiveRedisTemplate` | Reactive API for Redis operations |
| `ReactiveStringRedisTemplate` | Specialized template for String key-value pairs |
| Lettuce client | The underlying async Redis client (default in Spring Boot) |

### Why "reactive"?

Our backend uses WebFlux (non-blocking). A blocking Redis client would freeze the event loop. The reactive version returns `Mono<T>` and `Flux<T>` which integrate naturally with our WebFlux controllers.

---

# Step 3: Configure Redis in application.yml

**File: `backend/src/main/resources/application.yml`**

```yaml
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}

# Cache configuration
app:
  cache:
    response-ttl-minutes: 60
    rate-limit-requests-per-minute: 20
```

### Explanation:

| Property | Value | Purpose |
|----------|-------|---------|
| `host` | `localhost` (default) or `redis` (Docker) | Redis server hostname |
| `port` | `6379` | Redis default port |
| `response-ttl-minutes: 60` | Custom property | Cached responses expire after 1 hour |
| `rate-limit-requests-per-minute: 20` | Custom property | Max 20 questions per minute per user |

### How Spring Boot auto-configures:

When Spring Boot sees `spring-boot-starter-data-redis-reactive` on the classpath AND these properties, it automatically creates:
1. `LettuceConnectionFactory` (connection pool)
2. `ReactiveRedisConnectionFactory` bean
3. Connects to the specified host:port

---

# Step 4: Create RedisConfig.java

**File: `backend/src/main/java/com/contract/analyser/config/RedisConfig.java`**

```java
package com.contract.analyser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext(new StringRedisSerializer())
                .value(new StringRedisSerializer())
                .build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}
```

### Why this config is needed:

| Bean | Purpose |
|------|---------|
| `ReactiveStringRedisTemplate` | Pre-configured template for String↔String operations |
| `ReactiveRedisTemplate` | Template with explicit serializers (avoids binary garbled keys) |
| `StringRedisSerializer` | Ensures keys/values are stored as readable UTF-8 strings in Redis |

Without explicit serializers, Spring uses Java serialization by default — keys look like `\xac\xed\x00\x05t\x00\x10...` (unreadable in Redis CLI).

---

# Step 5: Create CacheService.java

**File: `backend/src/main/java/com/contract/analyser/service/CacheService.java`**

```java
@Service
public class CacheService {

    private static final String CACHE_PREFIX = "rag:response:";
    private final ReactiveStringRedisTemplate redisTemplate;
    private final Duration cacheTtl;

    public CacheService(ReactiveStringRedisTemplate redisTemplate,
                        @Value("${app.cache.response-ttl-minutes:60}") int ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = Duration.ofMinutes(ttlMinutes);
    }
```

### Key Design Decisions:

**1. Cache Key Format**: `rag:response:{contractId}:{userId}:{questionHash}`

```java
public String generateCacheKey(Long contractId, Long userId, String question) {
    String questionHash = hashQuestion(question.trim().toLowerCase());
    return CACHE_PREFIX + contractId + ":" + userId + ":" + questionHash;
}
```

- **Namespaced** (`rag:response:`) — avoids key collisions with other uses
- **Contract + User scoped** — multi-tenant isolation
- **Question hashed** (SHA-256, first 16 chars) — handles long questions, normalizes case/whitespace

Example: `rag:response:1:101:a3f2b8c1d4e5f678`

**2. Get cached response**:
```java
public Mono<String> getCachedResponse(String cacheKey) {
    return redisTemplate.opsForValue().get(cacheKey);
}
```
- Returns `Mono.empty()` if key doesn't exist (cache miss)
- Returns `Mono<String>` with the cached answer if found (cache hit)

**3. Store response with TTL**:
```java
public Mono<Boolean> cacheResponse(String cacheKey, String response) {
    return redisTemplate.opsForValue().set(cacheKey, response, cacheTtl);
}
```
- `cacheTtl` = 60 minutes — after that, Redis auto-deletes the entry
- Next request for same question will get fresh answer from LLM

**4. Invalidate on document upload**:
```java
public Mono<Long> invalidateContractCache(Long contractId) {
    String pattern = CACHE_PREFIX + contractId + ":*";
    return redisTemplate.keys(pattern)
            .collectList()
            .flatMap(keys -> {
                if (keys.isEmpty()) return Mono.just(0L);
                return redisTemplate.delete(keys.toArray(new String[0]));
            });
}
```
- When user uploads a new PDF, ALL cached answers for that contract are deleted
- Why? Old answers may reference content that no longer exists in the new document

---

# Step 6: Create RateLimiterService.java

**File: `backend/src/main/java/com/contract/analyser/service/RateLimiterService.java`**

```java
@Service
public class RateLimiterService {

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";
    private final ReactiveStringRedisTemplate redisTemplate;
    private final int maxRequestsPerMinute;

    public Mono<Boolean> isAllowed(Long userId) {
        String key = RATE_LIMIT_PREFIX + userId;

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request — set expiry to 1 minute
                        return redisTemplate.expire(key, Duration.ofMinutes(1))
                                .thenReturn(true);
                    }
                    return Mono.just(count <= maxRequestsPerMinute);
                });
    }
}
```

### How the Sliding Window Rate Limiter Works:

```
Request 1: INCR "ratelimit:101" → returns 1 (first request)
            EXPIRE "ratelimit:101" 60 → auto-delete after 60 seconds
            Result: ALLOWED (1 ≤ 20)

Request 2: INCR "ratelimit:101" → returns 2
            Result: ALLOWED (2 ≤ 20)

...

Request 21: INCR "ratelimit:101" → returns 21
            Result: BLOCKED (21 > 20)

After 60 seconds: Key auto-expires, counter resets to 0
```

### Why Redis for rate limiting:

| Reason | Explanation |
|--------|-------------|
| **Atomic** | `INCR` is a single atomic operation — no race conditions even with concurrent requests |
| **Auto-expiry** | `EXPIRE` handles cleanup automatically — no cron jobs |
| **Shared** | All backend pods share the same Redis → consistent limit across K8s replicas |
| **Fast** | In-memory operation — adds <1ms latency per request |

---

# Step 7: Create ChatHistoryService.java

**File: `backend/src/main/java/com/contract/analyser/service/ChatHistoryService.java`**

```java
@Service
public class ChatHistoryService {

    private static final String HISTORY_PREFIX = "chat:history:";
    private static final int MAX_MESSAGES = 50;
    private static final Duration HISTORY_TTL = Duration.ofHours(24);

    public Mono<Long> saveMessage(Long contractId, Long userId, String role, String content) {
        String key = HISTORY_PREFIX + contractId + ":" + userId;
        Map<String, String> message = Map.of("role", role, "content", content);
        String json = objectMapper.writeValueAsString(message);

        return redisTemplate.opsForList().rightPush(key, json)
                .flatMap(size -> {
                    if (size > MAX_MESSAGES) {
                        return redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1)
                                .thenReturn(size);
                    }
                    return redisTemplate.expire(key, HISTORY_TTL).thenReturn(size);
                });
    }
}
```

### Redis Data Structure: LIST

```
Key: "chat:history:1:101"
Value (List):
  [0] → {"role":"user","content":"What are the payment terms?"}
  [1] → {"role":"assistant","content":"The payment terms state..."}
  [2] → {"role":"user","content":"When does it expire?"}
  [3] → {"role":"assistant","content":"The contract expires on..."}
```

### Operations explained:

| Operation | Redis Command | Purpose |
|-----------|--------------|---------|
| `rightPush` | `RPUSH` | Append message to end of list (maintains order) |
| `trim(-50, -1)` | `LTRIM` | Keep only last 50 messages (memory protection) |
| `expire(24h)` | `EXPIRE` | Auto-delete after 24 hours of inactivity |
| `range(0, -1)` | `LRANGE` | Retrieve all messages (for the history endpoint) |

---

# Step 8: Update RagService.java (The Core Change)

**File: `backend/src/main/java/com/contract/analyser/service/RagService.java`**

### Before (Simple):
```java
public Flux<String> streamResponse(ChatRequest request) {
    // Vector search → LLM → Stream
}
```

### After (With Redis):
```java
public Flux<String> streamResponse(ChatRequest request) {
    // Step 1: Rate limit check
    return rateLimiterService.isAllowed(request.userId())
        .flatMapMany(allowed -> {
            if (!allowed) {
                return Flux.just("Rate limit exceeded...");
            }

            // Step 2: Cache check
            String cacheKey = cacheService.generateCacheKey(...);
            return cacheService.getCachedResponse(cacheKey)
                .flatMapMany(cached -> {
                    // CACHE HIT → return instantly
                    return Flux.just(cached);
                })
                .switchIfEmpty(
                    // CACHE MISS → full RAG pipeline
                    executeRagPipeline(request, cacheKey)
                );
        });
}
```

### The `executeRagPipeline` method:
```java
private Flux<String> executeRagPipeline(ChatRequest request, String cacheKey) {
    // ... vector search + LLM streaming (same as before) ...

    StringBuilder responseBuilder = new StringBuilder();

    return chatClient.prompt()
        .system(systemPrompt)
        .user(request.question())
        .stream()
        .content()
        .doOnNext(responseBuilder::append)          // Collect tokens
        .doOnComplete(() -> {
            String fullResponse = responseBuilder.toString();
            cacheService.cacheResponse(cacheKey, fullResponse).subscribe();  // Cache it
            chatHistoryService.saveMessage(..., "user", question).subscribe();
            chatHistoryService.saveMessage(..., "assistant", fullResponse).subscribe();
        });
}
```

### Key Pattern: `doOnNext` + `doOnComplete`

- **`doOnNext(responseBuilder::append)`**: As each token streams through, append it to a StringBuilder. The token STILL flows to the client (this is a side effect, not a transformation).
- **`doOnComplete(...)`**: After the last token, cache the complete response. This doesn't delay the stream — it fires asynchronously after streaming ends.

---

# Step 9: Update DocumentIngestionService.java

**Added after `vectorStore.add(chunks)`:**

```java
// Invalidate Redis cache for this contract (old answers are now stale)
cacheService.invalidateContractCache(DEFAULT_CONTRACT_ID).subscribe();

// Clear chat history (new document means fresh conversation)
chatHistoryService.clearHistory(DEFAULT_CONTRACT_ID, DEFAULT_USER_ID).subscribe();
```

### Why invalidate on upload?

If a user uploads a new contract and asks the same question, the answer should come from the NEW document, not a cached response from the old one.

**Example scenario without invalidation:**
1. Upload Contract v1 → "What is the termination clause?" → "60 days notice"
2. Upload Contract v2 (different terms) → same question → **"60 days notice" (WRONG — cached stale answer!)**

**With invalidation:**
1. Upload Contract v1 → cached
2. Upload Contract v2 → cache CLEARED → fresh LLM call → correct answer from new doc

---

# Step 10: Update ChatController.java

**Added new endpoint:**

```java
@GetMapping("/chat/history")
public Mono<List<String>> getChatHistory(
        @RequestParam("contract_id") Long contractId,
        @RequestParam("user_id") Long userId) {
    return chatHistoryService.getHistory(contractId, userId);
}
```

### Purpose:

When the frontend loads (or refreshes), it can call:
```
GET /api/chat/history?contract_id=1&user_id=101
```

Returns a JSON array of messages stored in Redis:
```json
[
  "{\"role\":\"user\",\"content\":\"What are the payment terms?\"}",
  "{\"role\":\"assistant\",\"content\":\"The payment terms state that...\"}"
]
```

The frontend can parse these and restore the chat UI without losing conversation history.

---

# Step 11: Kubernetes Redis Manifest

**File: `k8s/redis.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: contract-analyzer
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: redis
          image: redis:7-alpine
          command: ["redis-server", "--appendonly", "yes"]
          ports:
            - containerPort: 6379
          volumeMounts:
            - name: redis-storage
              mountPath: /data
          resources:
            requests:
              memory: "128Mi"
              cpu: "100m"
            limits:
              memory: "256Mi"
              cpu: "250m"
---
apiVersion: v1
kind: Service
metadata:
  name: redis-service
  namespace: contract-analyzer
spec:
  selector:
    app: redis
  ports:
    - port: 6379
      targetPort: 6379
```

Backend pods connect to `redis-service:6379` via Kubernetes DNS.

---

# Complete Data Flow with Redis

```
┌──────────────────────────────────────────────────────────────────┐
│                    REQUEST FLOW                                    │
│                                                                    │
│  User: "What are the payment terms?"                              │
│       ↓                                                            │
│  ┌─────────────────────────┐                                      │
│  │ 1. RATE LIMIT CHECK     │                                      │
│  │    Redis: INCR           │                                      │
│  │    "ratelimit:101" → 5   │                                      │
│  │    5 ≤ 20? → ALLOWED    │                                      │
│  └───────────┬─────────────┘                                      │
│              ↓                                                      │
│  ┌─────────────────────────┐                                      │
│  │ 2. CACHE CHECK          │                                      │
│  │    Redis: GET            │                                      │
│  │    "rag:response:1:101:  │                                      │
│  │     a3f2b8c1d4e5f678"   │                                      │
│  └───────┬─────────┬───────┘                                      │
│          ↓ HIT     ↓ MISS                                          │
│   ┌──────────┐  ┌────────────────────────────────────┐            │
│   │ Return   │  │ 3. VECTOR SEARCH (PostgreSQL)      │            │
│   │ cached   │  │ 4. EMBED QUESTION (Ollama)         │            │
│   │ response │  │ 5. LLM GENERATE (Ollama streaming) │            │
│   │ (2ms)    │  │ 6. CACHE RESPONSE (Redis SET)      │            │
│   └──────────┘  │ 7. SAVE TO HISTORY (Redis RPUSH)   │            │
│                  │ (5-10 seconds first time)          │            │
│                  └────────────────────────────────────┘            │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                    UPLOAD FLOW                                     │
│                                                                    │
│  User uploads new PDF                                             │
│       ↓                                                            │
│  1. Extract text (Tika)                                           │
│  2. Chunk text (TokenTextSplitter)                                │
│  3. Delete old vectors (PostgreSQL)                               │
│  4. Store new vectors (PostgreSQL + Ollama embeddings)            │
│  5. INVALIDATE CACHE (Redis: DEL rag:response:1:*)  ← NEW        │
│  6. CLEAR HISTORY (Redis: DEL chat:history:1:101)   ← NEW        │
└──────────────────────────────────────────────────────────────────┘
```

---

# Redis Data Structures Used

| Structure | Redis Command | Key Pattern | Use Case |
|-----------|--------------|-------------|----------|
| **String** | `SET`, `GET` | `rag:response:{cid}:{uid}:{hash}` | Cache LLM responses |
| **String (counter)** | `INCR`, `EXPIRE` | `ratelimit:{uid}` | Rate limiting |
| **List** | `RPUSH`, `LRANGE`, `LTRIM` | `chat:history:{cid}:{uid}` | Chat message history |

### Example Redis CLI inspection:

```bash
# Connect to Redis
docker exec -it contract-redis redis-cli

# View all keys
KEYS *

# Check a cached response
GET rag:response:1:101:a3f2b8c1d4e5f678

# Check rate limit counter
GET ratelimit:101

# View chat history
LRANGE chat:history:1:101 0 -1

# Check TTL (seconds remaining)
TTL rag:response:1:101:a3f2b8c1d4e5f678

# Manual cache clear
DEL rag:response:1:101:a3f2b8c1d4e5f678
```

---

# Interview Q&A

### Q1: Why Redis instead of an in-memory HashMap?

| In-Memory HashMap | Redis |
|-------------------|-------|
| Lives in one JVM instance | Shared across all backend pods |
| Lost on restart | Persisted (AOF) |
| Can't enforce TTL natively | Built-in EXPIRE |
| Race conditions in multi-threaded | Atomic operations |
| No visibility/debugging | Redis CLI for inspection |

**Answer**: In a Kubernetes environment with multiple backend replicas, an in-memory cache is per-pod. If Pod A caches a response, Pod B doesn't have it. Redis provides a shared, persistent, observable cache that all pods access equally.

---

### Q2: Why TTL (Time-To-Live) on cached responses?

**Answer**: Contract documents may be updated. If we cache forever, users get stale answers. A 1-hour TTL balances:
- Performance (cache hits for 1 hour)
- Freshness (eventual consistency after expiry)
- Memory management (Redis doesn't grow unbounded)

We also actively invalidate on upload (belt + suspenders).

---

### Q3: What happens if Redis goes down?

**Answer**: The application continues working — Redis is a cache, not a primary data store. The `getCachedResponse()` returns `Mono.empty()` on failure, which triggers `switchIfEmpty()` → runs the full RAG pipeline. Rate limiting also fails open (allows all requests). This is the **Cache-Aside pattern** — cache is optional, not critical.

---

### Q4: Why SHA-256 for the cache key instead of the raw question?

**Answer**:
1. **Length**: Questions can be 500+ characters → Redis key limits
2. **Normalization**: "What are the payment terms?" and "what are the payment terms?" should hit the same cache entry → `.trim().toLowerCase()` before hashing
3. **Consistency**: Fixed-length 16-char hash regardless of question length
4. **Special characters**: Questions may contain spaces, quotes, unicode → hash avoids encoding issues

---

### Q5: Why `--appendonly yes` in Redis config?

**Answer**: AOF (Append Only File) persistence writes every write operation to disk. If Redis restarts, it replays the AOF file to restore state. Without it, all cached data is lost on restart. Trade-off: slightly slower writes (~1ms) but data survives crashes.

---

### Q6: Why reactive Redis (`spring-boot-starter-data-redis-reactive`)?

**Answer**: Our backend uses Spring WebFlux (non-blocking event loop with ~4 threads). A blocking Redis call (`spring-boot-starter-data-redis`) would hold an event loop thread hostage for the entire Redis round-trip. The reactive version returns `Mono<T>` — the thread is released immediately and continues serving other requests. When Redis responds, the pipeline resumes on any available thread.

---

### Q7: What is the Cache-Aside pattern?

**Answer**: 
1. Application checks cache first
2. If found (hit): return cached value
3. If not found (miss): fetch from source (LLM), store in cache, return
4. On data change: invalidate cache

This is exactly what our `RagService` does. Alternative patterns: Write-Through (write to cache AND source simultaneously), Write-Behind (write to cache, async flush to source).

---

### Q8: How does rate limiting work across multiple Kubernetes pods?

```
Pod 1 receives request → INCR "ratelimit:101" → Redis returns 15
Pod 2 receives request → INCR "ratelimit:101" → Redis returns 16
Pod 3 receives request → INCR "ratelimit:101" → Redis returns 17
```

All pods share the same Redis instance → the counter is global. Without Redis, each pod would have its own counter (user could make 20 × 3 = 60 requests/minute by hitting different pods).

---

### Q9: Why `subscribe()` on cache/history writes in the pipeline?

```java
cacheService.cacheResponse(cacheKey, fullResponse).subscribe();
```

**Answer**: Fire-and-forget. We don't want to block the response stream waiting for Redis to confirm the write. The user gets their streaming answer immediately; caching happens asynchronously in the background. If the cache write fails, no harm — next request just does a fresh LLM call.

---

### Q10: How would you scale Redis in production?

| Level | Solution |
|-------|---------|
| **Single server** | Current setup — handles ~100K ops/sec |
| **High availability** | Redis Sentinel (automatic failover) |
| **Massive scale** | Redis Cluster (shards data across multiple nodes) |
| **Managed service** | AWS ElastiCache, GCP Memorystore, Azure Cache for Redis |

For this project, a single Redis instance handles well over 10,000 concurrent users.

---

*End of Document*
