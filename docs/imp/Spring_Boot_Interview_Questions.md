# Spring Boot Interview Questions — Experienced Level (5-10 Years)
## With Answers Referencing Our Contract Analyzer Project

---

# SECTION 1: Core Spring Boot Concepts

---

### Q1: What is the difference between @Component, @Service, @Repository, and @Controller?

**Answer**: All four are stereotype annotations that tell Spring to create a bean. The difference is semantic (readability) + some extra behavior:

| Annotation | Purpose | Extra Behavior |
|-----------|---------|----------------|
| `@Component` | Generic bean | None |
| `@Service` | Business logic layer | None (just clarity) |
| `@Repository` | Data access layer | Auto-translates SQL exceptions to Spring's DataAccessException |
| `@Controller` | Web MVC controller | Returns views |
| `@RestController` | REST API controller | `@Controller` + `@ResponseBody` (returns JSON, not views) |

**In our project**:
- `@Service` → `RagService`, `CacheService`, `DocumentIngestionService`
- `@RestController` → `ChatController`, `UploadController`
- `@Configuration` → `CorsConfig`, `AiConfig`, `RedisConfig`

---

### Q2: Explain Dependency Injection in Spring. How does constructor injection work?

**Answer**: DI means Spring creates objects and provides ("injects") their dependencies automatically. You declare what you need; Spring delivers it.

```java
// In our project (ChatController.java):
@RestController
public class ChatController {

    private final RagService ragService;
    private final ChatHistoryService chatHistoryService;

    // Constructor injection — Spring auto-provides these beans
    public ChatController(RagService ragService, ChatHistoryService chatHistoryService) {
        this.ragService = ragService;
        this.chatHistoryService = chatHistoryService;
    }
}
```

**Why constructor injection over @Autowired field injection?**
1. Fields can be `final` (immutable, thread-safe)
2. Makes dependencies explicit (easy to see what a class needs)
3. Easier to unit test (pass mocks via constructor)
4. Fails fast at startup if dependency is missing (not at runtime)

---

### Q3: What does @SpringBootApplication do internally?

**Answer**: It's a meta-annotation combining three annotations:

```java
@SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
```

| Sub-annotation | What it does |
|---------------|-------------|
| `@Configuration` | Marks this class as a source of bean definitions |
| `@EnableAutoConfiguration` | Scans classpath jars and auto-configures beans (e.g., sees PostgreSQL driver → creates DataSource) |
| `@ComponentScan` | Scans current package + sub-packages for @Component, @Service, @Controller, etc. |

**Our project**: `ContractAnalyserApplication` is in `com.contract.analyser` → Spring scans all packages under that root.

---

### Q4: How does Spring Boot auto-configuration work? Give an example.

**Answer**: Spring Boot looks at what libraries are on the classpath and automatically configures beans you'd otherwise define manually.

**Example from our project**:

1. `spring-ai-pgvector-store-spring-boot-starter` is in `pom.xml`
2. `application.yml` has `spring.ai.vectorstore.pgvector.dimensions: 768`
3. Spring Boot sees the starter → auto-creates a `PgVectorStore` bean
4. Our `DocumentIngestionService` just injects `VectorStore` — no manual configuration needed

**How it works internally**:
- Each starter has a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file
- Lists condition-based `@Configuration` classes
- Conditions: `@ConditionalOnClass`, `@ConditionalOnProperty`, `@ConditionalOnMissingBean`

---

### Q5: What is the difference between application.properties and application.yml?

**Answer**: Same purpose, different syntax. YAML is hierarchical and more readable for nested config.

```properties
# application.properties (flat)
spring.datasource.url=jdbc:postgresql://localhost:5432/contractdb
spring.datasource.username=postgres
```

```yaml
# application.yml (hierarchical)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/contractdb
    username: postgres
```

**We use YAML** because our config is deeply nested (`spring.ai.ollama.chat.options.model`).

---

### Q6: How do you externalize configuration in Spring Boot? How does ${VARIABLE:default} work?

**Answer**: Spring Boot resolves values in this order (highest priority first):
1. Command-line arguments (`--server.port=9090`)
2. Environment variables (`SPRING_DATASOURCE_URL=...`)
3. `application.yml` / `application.properties`
4. Default values in code

**Our project uses environment variable injection**:
```yaml
url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/contractdb}
```
- If `SPRING_DATASOURCE_URL` env var exists → use its value
- If not → use the default after the colon (`:`)
- Docker Compose sets the env var → overrides the default

---

### Q7: What is the Bean lifecycle in Spring?

**Answer**:
1. **Instantiation** — Spring creates the object (calls constructor)
2. **Dependency Injection** — Sets fields / calls setters
3. **`@PostConstruct`** — Initialization callback
4. **Ready** — Bean is available for use
5. **`@PreDestroy`** — Cleanup callback (on shutdown)
6. **Destruction** — Bean is garbage collected

---

# SECTION 2: Spring WebFlux & Reactive

---

### Q8: What is the difference between Spring MVC and Spring WebFlux?

| Aspect | Spring MVC | Spring WebFlux |
|--------|-----------|----------------|
| Server | Tomcat (servlet) | Netty (non-blocking) |
| Threading | 1 thread per request | Few event-loop threads |
| Return types | `T`, `ResponseEntity<T>` | `Mono<T>`, `Flux<T>` |
| Blocking I/O | Allowed | FORBIDDEN on event loop |
| Use case | Traditional REST APIs | Streaming, high concurrency |
| Annotation | `@Controller` | Same annotations, different internals |

**Why we chose WebFlux**: Our chat endpoint streams tokens from the LLM one by one (`Flux<String>`). Spring MVC can't stream efficiently — it would buffer the entire response.

---

### Q9: Why can't you use blocking code on the WebFlux event loop? What happens?

**Answer**: WebFlux uses ~4 event-loop threads to handle thousands of requests. If one thread blocks (e.g., `Thread.sleep()`, blocking DB call), that thread can't serve ANY other request — effectively reducing capacity by 25% per blocked thread.

**Our solution** — offload blocking work:
```java
Mono.fromCallable(() -> ingestionService.ingestDocument(file))
    .subscribeOn(Schedulers.boundedElastic());
```
- `Schedulers.boundedElastic()` provides a separate thread pool (up to 10 × CPU cores)
- The blocking PDF processing runs there — event loop stays free

---

### Q10: What is backpressure in reactive streams?

**Answer**: When a producer (Ollama LLM) generates data faster than the consumer (slow network client) can handle. Backpressure is the consumer saying "slow down, I can't keep up."

**Example**: LLM generates 100 tokens/second but the user's slow mobile connection can only receive 10 tokens/second. Without backpressure, memory fills up. With backpressure, the publisher pauses until the subscriber is ready.

Reactive Streams specification mandates backpressure support — `Flux` handles this automatically.

---

### Q11: Explain `flatMap` vs `map` in reactive programming.

```java
// map: Transform value synchronously (no async operations inside)
mono.map(user -> user.getName())  // User → String

// flatMap: Transform value into another Mono/Flux (async operations inside)
mono.flatMap(user -> findOrdersForUser(user.getId()))  // User → Mono<List<Order>>
```

**Rule**: If the transformation involves a Mono/Flux (DB call, API call), use `flatMap`. If it's a simple in-memory operation, use `map`.

**Our project**:
```java
rateLimiterService.isAllowed(request.userId())      // Returns Mono<Boolean>
    .flatMapMany(allowed -> { ... })                // Boolean → Flux<String>
```

---

# SECTION 3: REST API Design & Controllers

---

### Q12: What is the difference between @RequestParam, @PathVariable, @RequestBody, and @RequestPart?

| Annotation | Source | Example |
|-----------|--------|---------|
| `@RequestParam` | Query string | `GET /api?user_id=101` → `@RequestParam("user_id") Long userId` |
| `@PathVariable` | URL path | `GET /api/users/101` → `@PathVariable Long id` |
| `@RequestBody` | JSON body | POST body → `@RequestBody ChatRequest request` |
| `@RequestPart` | Multipart form | File upload → `@RequestPart("file") FilePart file` |

**Our project uses all of these**:
- `@RequestBody` in `ChatController` (JSON payload)
- `@RequestPart` in `UploadController` (file upload)
- `@RequestParam` in `ChatController.getChatHistory()` (query params)

---

### Q13: How do you handle file uploads in WebFlux?

**Answer**: WebFlux uses `FilePart` (reactive) instead of MVC's `MultipartFile` (blocking).

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Mono<Map<String, Object>> uploadDocument(@RequestPart("file") FilePart filePart) {
    return Mono.fromCallable(() -> ingestionService.ingestDocument(filePart))
            .subscribeOn(Schedulers.boundedElastic());
}
```

- `FilePart` — reactive file part, content streamed as `Flux<DataBuffer>`
- `filePart.transferTo(file)` — writes to disk reactively, returns `Mono<Void>`
- Wrapped in `Schedulers.boundedElastic()` because Tika PDF reading is blocking

---

### Q14: How do you implement Server-Sent Events (SSE) in Spring WebFlux?

**Answer**:
```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestBody ChatRequest request) {
    return ragService.streamResponse(request);
}
```

- `produces = TEXT_EVENT_STREAM_VALUE` → sets `Content-Type: text/event-stream`
- Return type `Flux<String>` → Spring auto-formats each element as an SSE event (`data: token\n\n`)
- Connection stays open until Flux completes
- Client reads with `EventSource` or `fetch` + `ReadableStream`

---

### Q15: How do you handle CORS in a Spring WebFlux application?

**Answer**: Use `CorsWebFilter` (not Spring MVC's `@CrossOrigin` or `WebMvcConfigurer`).

```java
@Bean
public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
}
```

**Key point**: In WebFlux, everything is a `WebFilter` (not a servlet filter). The reactive filter chain processes requests non-blockingly.

---

# SECTION 4: Database & Data Layer

---

### Q16: What is the difference between JdbcTemplate and JPA/Hibernate? Why did we choose JdbcTemplate?

| JdbcTemplate | JPA/Hibernate |
|-------------|--------------|
| Raw SQL queries | Object-relational mapping (ORM) |
| Lightweight, no magic | Heavy, complex under the hood |
| Full control over queries | Generates SQL (may be suboptimal) |
| No entity management | Manages entity lifecycle |

**Why JdbcTemplate in our project**: We only need one simple query:
```java
jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'contract_id' = ?", ...);
```
JPA doesn't support pgvector's `vector(768)` type natively. JdbcTemplate gives us full control over PostgreSQL-specific JSONB operators (`->>'key'`).

---

### Q17: How does database connection pooling work in Spring Boot?

**Answer**: Spring Boot uses HikariCP (default) as the connection pool.

Instead of creating a new DB connection per request (expensive — TCP handshake, auth), a pool pre-creates connections and reuses them.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/contractdb
    # HikariCP auto-configured with defaults:
    # maximum-pool-size: 10
    # minimum-idle: 10
    # connection-timeout: 30000ms
```

Spring Boot auto-creates a `DataSource` bean backed by HikariCP → `JdbcTemplate` and `PgVectorStore` both use it.

---

### Q18: How do you handle database initialization in Spring Boot?

**Answer**: Multiple approaches:

| Method | When to Use |
|--------|-------------|
| `schema.sql` in classpath | Simple schema setup |
| Flyway/Liquibase | Production migrations (versioned) |
| Docker volume mount | Our approach — mount SQL into init directory |

**Our project**: Mount `schema.sql` into `/docker-entrypoint-initdb.d/` — PostgreSQL runs it on first startup automatically.

---

# SECTION 5: Security & Best Practices

---

### Q19: How do you manage secrets in Spring Boot applications?

| Environment | Approach |
|-------------|----------|
| Local dev | `application.yml` with defaults |
| Docker | Environment variables in `docker-compose.yml` |
| Kubernetes | `Secret` objects (base64, or encrypted with Sealed Secrets) |
| Cloud | AWS Secrets Manager, GCP Secret Manager, HashiCorp Vault |

**Our project**:
```yaml
# Docker passes secrets as env vars:
SPRING_DATASOURCE_PASSWORD: postgres

# application.yml reads with fallback:
password: ${SPRING_DATASOURCE_PASSWORD:postgres}

# K8s uses Secret object:
secretKeyRef:
  name: db-credentials
  key: POSTGRES_PASSWORD
```

---

### Q20: What is the difference between @Value and @ConfigurationProperties?

```java
// @Value — single property injection
@Value("${app.cache.response-ttl-minutes:60}")
private int ttlMinutes;

// @ConfigurationProperties — binds entire group
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(int responseTtlMinutes, int rateLimitRequestsPerMinute) {}
```

**When to use which**:
- 1-2 values → `@Value`
- Many related values → `@ConfigurationProperties` (type-safe, validated)

---

### Q21: How do you implement rate limiting in a distributed Spring Boot app?

**Answer** (from our project):

```java
public Mono<Boolean> isAllowed(Long userId) {
    String key = "ratelimit:" + userId;
    return redisTemplate.opsForValue().increment(key)
        .flatMap(count -> {
            if (count == 1) {
                return redisTemplate.expire(key, Duration.ofMinutes(1)).thenReturn(true);
            }
            return Mono.just(count <= maxRequestsPerMinute);
        });
}
```

**Why Redis**: Atomic INCR across all pods. In-memory counter = per-pod (bypassed by round-robin load balancer).

---

# SECTION 6: Microservices & Docker

---

### Q22: How does service discovery work in Docker Compose vs Kubernetes?

| Docker Compose | Kubernetes |
|---------------|-----------|
| Service name = DNS hostname | Service name = DNS hostname |
| `db:5432` resolves to container IP | `postgres-service:5432` resolves to ClusterIP |
| Docker built-in DNS | CoreDNS |
| Flat network (one bridge) | Pod network + Service abstraction |

**Our project**:
- Docker: `jdbc:postgresql://db:5432/contractdb` (`db` = service name)
- K8s: `jdbc:postgresql://postgres-service:5432/contractdb` (`postgres-service` = Service name)

---

### Q23: What is a multi-stage Docker build? Why use it?

```dockerfile
# Stage 1: BUILD (has JDK + Maven — 700MB)
FROM eclipse-temurin:21-jdk AS build
COPY . .
RUN ./mvnw clean package

# Stage 2: RUN (has only JRE — 300MB)
FROM eclipse-temurin:21-jre
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Benefits**:
- Final image is 50% smaller (no build tools)
- Smaller attack surface (no compiler, no source code)
- Faster deployments (smaller image to pull)

---

### Q24: How do you handle health checks in Spring Boot?

**Answer**: Add `spring-boot-starter-actuator`:
- `GET /actuator/health` → returns `{"status": "UP"}` or `{"status": "DOWN"}`
- Can include custom indicators (DB connectivity, Redis availability, Ollama status)

**Used in Kubernetes**:
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 30
```

K8s calls this every 10 seconds. If it returns non-200, pod is removed from load balancer.

---

# SECTION 7: Spring AI Specific (Bonus)

---

### Q25: What is the ChatClient fluent API in Spring AI?

**Answer**:
```java
chatClient.prompt()
    .system("You are a contract analyst...")  // System instructions
    .user("What are the payment terms?")     // User question
    .stream()                                 // Enable streaming
    .content();                              // Get Flux<String> of tokens
```

- Builder pattern — compose prompts step by step
- Supports `.call()` (blocking) and `.stream()` (reactive)
- Works with any AI provider (Ollama, OpenAI, Anthropic) — just swap the underlying model

---

### Q26: What is a VectorStore in Spring AI? How does similarity search work?

**Answer**: `VectorStore` is an abstraction for storing and querying vector embeddings:

```java
// Store: text → embed via Ollama → save vector to PostgreSQL
vectorStore.add(documents);

// Search: question → embed → find similar vectors → return documents
vectorStore.similaritySearch(SearchRequest.query("payment terms").withTopK(3));
```

**Under the hood**:
1. Question converted to 768-dim vector via `nomic-embed-text`
2. PostgreSQL uses HNSW index to find closest vectors (cosine similarity)
3. Returns top 3 document chunks

---

### Q27: What is the difference between Semantic Search and Keyword Search?

| Keyword Search (LIKE/Full-Text) | Semantic Search (Vector) |
|--------------------------------|--------------------------|
| Matches exact words | Matches meaning |
| "payment terms" finds "payment terms" | "payment terms" also finds "when to pay" |
| Fast but dumb | Slower but intelligent |
| SQL `LIKE '%payment%'` | pgvector cosine similarity |

**Our project uses semantic search** — the user doesn't need to know the exact words in the contract.

---

# SECTION 8: Design Patterns Used in This Project

---

### Q28: What design patterns can you identify in this project?

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Builder** | `ChatClient.builder()`, `SearchRequest.query()` | Fluent object construction |
| **Strategy** | `VectorStore` interface, `TokenTextSplitter` | Swap implementations without code changes |
| **Cache-Aside** | `CacheService` + `RagService` | Check cache → miss → compute → store |
| **Template Method** | `ReactiveRedisTemplate` | Standard operations with hooks |
| **Dependency Injection** | All services | Loose coupling, testability |
| **DTO (Data Transfer Object)** | `ChatRequest` record | Separate API contract from internal model |
| **Chain of Responsibility** | Rate limit → Cache → RAG pipeline | Each step decides pass/fail |

---

### Q29: Explain the Cache-Aside pattern as implemented in our RagService.

```
1. Application receives request
2. Check cache (Redis GET)
3a. Cache HIT → return cached value (fast path)
3b. Cache MISS → compute result (slow path: vector search + LLM)
4. Store result in cache (Redis SET with TTL)
5. Return result to user
6. On data change (upload) → invalidate cache (Redis DEL pattern:*)
```

**Why not Write-Through?** We don't want to cache BEFORE the user gets the response. Stream first (UX), cache after (optimization).

---

### Q30: How would you make this project production-ready? What's missing?

**Answer**:

| Category | What to Add |
|----------|-------------|
| **Security** | Spring Security + JWT authentication, HTTPS (TLS) |
| **Observability** | Micrometer metrics, distributed tracing (Zipkin), structured logging (JSON) |
| **Resilience** | Circuit breaker (Resilience4j) for Ollama calls, retry with exponential backoff |
| **API Documentation** | OpenAPI/Swagger with SpringDoc |
| **Testing** | Unit tests (JUnit 5 + Mockito), integration tests (Testcontainers) |
| **CI/CD** | GitHub Actions pipeline (build → test → push image → deploy) |
| **Multi-tenancy** | Dynamic contract_id/user_id from JWT claims (not hardcoded) |
| **Error Handling** | Global `@ControllerAdvice` with structured error responses |

---

*End of Document — Good luck with your interview!*
