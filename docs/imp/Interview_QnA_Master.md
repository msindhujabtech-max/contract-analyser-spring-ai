# Interview Q&A Master Document
## Java 8 · Spring Boot · Microservices · Design Patterns · Coding

Every answer has a **short explanation** + a **small example** so you can understand AND explain it to the interviewer.

---

# SECTION A: JAVA CORE & JAVA 8

---

### A1. What is a record class? Why do we have it in Java?

**Answer**: A `record` (Java 16+) is a special class for holding immutable data. It auto-generates the constructor, getters, `equals()`, `hashCode()`, and `toString()` — so you write less boilerplate.

**Why**: Before records, a simple data class needed 40+ lines. A record does it in 1 line.

**Example**:
```java
// Old way — 40 lines of boilerplate
// New way — 1 line:
public record Employee(int id, String name, double salary) {}

Employee e = new Employee(1, "Riaan", 50000);
System.out.println(e.name());     // "Riaan" (auto getter)
System.out.println(e);            // Employee[id=1, name=Riaan, salary=50000.0]
```

**In my project**: `ChatRequest` is a record holding `contractId`, `userId`, `question`.

---

### A2. Can a record class have user-defined constructors?

**Answer**: Yes. You can add a **compact constructor** (for validation) or a full **canonical constructor**.

**Example**:
```java
public record Employee(int id, String name, double salary) {
    // Compact constructor — for validation
    public Employee {
        if (salary < 0) throw new IllegalArgumentException("Salary can't be negative");
        name = name.trim();   // can modify before assignment
    }
    // Additional constructor
    public Employee(int id, String name) {
        this(id, name, 0.0);  // must call canonical constructor
    }
}
```

---

### A3. Which collection maintains insertion order, removes duplicates, and gives faster retrieval?

**Answer**: `LinkedHashSet`.
- **Set** → removes duplicates
- **LinkedHashSet** → maintains insertion order (unlike HashSet)
- **Hash-based** → O(1) fast retrieval

**Example**:
```java
Set<String> set = new LinkedHashSet<>();
set.add("apple"); set.add("banana"); set.add("apple"); set.add("cherry");
System.out.println(set);  // [apple, banana, cherry]  — order kept, duplicate removed
```

**Quick comparison**:
| Collection | Order | Duplicates | Speed |
|-----------|-------|-----------|-------|
| HashSet | No order | Removed | O(1) |
| **LinkedHashSet** | **Insertion order** | **Removed** | **O(1)** |
| TreeSet | Sorted order | Removed | O(log n) |
| ArrayList | Insertion order | Allowed | O(1) index |

---

### A4. Remove duplicate strings using Stream API

**Answer**: Use `.distinct()`.

**Example**:
```java
List<String> names = List.of("A", "B", "A", "C", "B");
List<String> unique = names.stream()
                           .distinct()
                           .collect(Collectors.toList());
// [A, B, C]
```

---

### A5. Remove duplicate strings WITHOUT Stream API (your own logic)

**Answer**: Use a Set to track seen items.

**Example**:
```java
List<String> input = Arrays.asList("A", "B", "A", "C", "B");
List<String> result = new ArrayList<>();
Set<String> seen = new HashSet<>();

for (String s : input) {
    if (seen.add(s)) {      // add() returns false if already present
        result.add(s);
    }
}
// result = [A, B, C]
```

---

### A6. Remove duplicates from a List of Employee objects

**Answer**: Duplicates in objects need `equals()`/`hashCode()` OR distinct by a field.

**Example (distinct by whole object — record auto-implements equals)**:
```java
List<Employee> unique = employees.stream().distinct().collect(Collectors.toList());
```

**Example (distinct by a field, e.g., id)**:
```java
List<Employee> unique = employees.stream()
    .collect(Collectors.toMap(
        Employee::id,        // key = id
        e -> e,              // value = employee
        (e1, e2) -> e1))     // on duplicate key, keep first
    .values().stream().collect(Collectors.toList());
```

---

### A7. Remove duplicate integers WITHOUT Java 8 Streams (coding question)

```java
public static List<Integer> removeDuplicates(List<Integer> input) {
    List<Integer> result = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (Integer num : input) {
        if (!seen.contains(num)) {
            seen.add(num);
            result.add(num);
        }
    }
    return result;
}
// Input:  [1, 2, 2, 3, 1, 4]
// Output: [1, 2, 3, 4]
```

---

### A8. Find employee with second-highest salary (Java 8)

```java
Optional<Employee> second = employees.stream()
    .sorted(Comparator.comparingDouble(Employee::salary).reversed())
    .skip(1)          // skip the highest
    .findFirst();

// Better (handles duplicate top salaries):
Optional<Employee> secondDistinct = employees.stream()
    .sorted(Comparator.comparingDouble(Employee::salary).reversed())
    .map(Employee::salary).distinct()
    .skip(1).findFirst()
    .flatMap(sal -> employees.stream().filter(e -> e.salary() == sal).findFirst());
```

---

### A9. What is the `final` keyword?

**Answer**: `final` means "cannot be changed."
- **final variable** → value can't be reassigned
- **final method** → can't be overridden
- **final class** → can't be extended (subclassed)

**Example**:
```java
final int MAX = 100;          // MAX = 200; → compile error
final class Constants {}      // class Sub extends Constants {} → error
class Parent { final void show() {} }  // can't override show()
```

**In my project**: All injected dependencies are `final` (constructor injection) → immutable, thread-safe:
```java
private final RagService ragService;
```

---

### A10. What are functional interfaces? Why needed if they have one abstract method?

**Answer**: A functional interface has exactly ONE abstract method. It's needed because **lambda expressions require a target type** — the lambda "becomes" an instance of that interface.

**Why not just methods?** Lambdas are objects. Java needs an interface type to represent them. `@FunctionalInterface` enforces the single-method rule.

**Example**:
```java
@FunctionalInterface
interface Calculator { int operate(int a, int b); }

Calculator add = (a, b) -> a + b;       // lambda = implementation
System.out.println(add.operate(2, 3));  // 5
```

---

### A11. Built-in functional interfaces in Java 8

| Interface | Method | Purpose | Example |
|-----------|--------|---------|---------|
| `Predicate<T>` | test(T) → boolean | Condition | `x -> x > 5` |
| `Function<T,R>` | apply(T) → R | Transform | `x -> x.length()` |
| `Consumer<T>` | accept(T) → void | Consume | `x -> print(x)` |
| `Supplier<T>` | get() → T | Provide | `() -> new User()` |
| `BiFunction<T,U,R>` | apply(T,U) → R | 2-input transform | `(a,b) -> a+b` |

**Real project use**:
```java
// Predicate — filter chunks
chunks.stream().filter(c -> c.getContent().length() > 100)
// Function — extract content
relevantDocs.stream().map(Document::getContent)
// Consumer — process each token
.doOnNext(token -> responseBuilder.append(token))
```

---

### A12. Which Java 8 feature do you use most? Why?

**Answer**: **Streams** — because they make data processing declarative and readable. Instead of loops with mutable state, I describe *what* I want.

**Example (from my project)**:
```java
String context = relevantDocs.stream()
    .map(Document::getContent)
    .collect(Collectors.joining("\n\n---\n\n"));
```

---

### A13. Which design pattern do Java 8 Streams use?

**Answer**: The **Builder / Pipeline pattern** (fluent chaining) combined with **Iterator pattern** internally. Each intermediate operation (`filter`, `map`) returns a new stream, building a pipeline that executes lazily on a terminal operation (`collect`, `forEach`).

---

### A14. What is CompletableFuture? How does it work?

**Answer**: `CompletableFuture` (Java 8) represents an async computation that will complete in the future. You can chain callbacks, combine multiple futures, and handle results without blocking.

**Example**:
```java
CompletableFuture.supplyAsync(() -> fetchUserFromDb(1))   // async task
    .thenApply(user -> user.getName())                     // transform result
    .thenAccept(name -> System.out.println(name))          // consume
    .exceptionally(ex -> { log.error("failed", ex); return null; });
```

---

### A15. CompletableFuture vs Future

| Future (Java 5) | CompletableFuture (Java 8) |
|-----------------|----------------------------|
| `get()` blocks the thread | Non-blocking callbacks (`thenApply`) |
| Can't chain operations | Chainable pipeline |
| Can't combine multiple | `thenCombine`, `allOf`, `anyOf` |
| No exception handling | `exceptionally`, `handle` |
| Can't manually complete | `complete(value)` |

**Example**:
```java
// Future — must block
Future<String> f = executor.submit(() -> "hello");
String result = f.get();   // BLOCKS here

// CompletableFuture — non-blocking
CompletableFuture.supplyAsync(() -> "hello")
    .thenApply(String::toUpperCase);   // no blocking
```

---

# SECTION B: SPRING & SPRING BOOT

---

### B1. What is the purpose of @Bean annotation?

**Answer**: `@Bean` marks a method whose return value becomes a Spring-managed object (bean). Used inside `@Configuration` classes to manually create beans — especially for third-party classes you can't annotate with `@Component`.

**Example (from my project)**:
```java
@Configuration
public class AiConfig {
    @Bean
    public ChatClient chatClient(OllamaChatModel model) {
        return ChatClient.builder(model).build();
    }
}
```
`ChatClient` is a Spring AI library class — I can't put `@Component` on it, so I create it with `@Bean`.

---

### B2. When to use @Bean instead of @Component?

**Answer**:
- **@Component** → on YOUR OWN classes (Spring scans and instantiates)
- **@Bean** → on THIRD-PARTY classes or when you need custom construction logic

**Example**:
```java
// @Component — my own service
@Service
public class RagService { }

// @Bean — third-party class I can't modify
@Bean
public ObjectMapper objectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
}
```

---

### B3. @Component vs @Service vs @Repository vs @Controller

| Annotation | Layer | Extra Behavior |
|-----------|-------|----------------|
| `@Component` | Generic | None |
| `@Service` | Business logic | Semantic clarity |
| `@Repository` | Data access | Translates DB exceptions to `DataAccessException` |
| `@Controller` | Web (returns views) | View resolution |
| `@RestController` | REST API | `@Controller` + `@ResponseBody` (returns JSON) |

**Example**: In my project — `@RestController ChatController`, `@Service RagService`, `@Configuration AiConfig`.

---

### B4. What is Dependency Injection?

**Answer**: Instead of a class creating its own dependencies, Spring provides them. Promotes loose coupling and testability.

**Example**:
```java
@Service
public class ChatController {
    private final RagService ragService;
    // Spring INJECTS ragService via constructor
    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }
}
```

---

### B5. Frequently used Spring annotations in my project

`@SpringBootApplication`, `@RestController`, `@Service`, `@Configuration`, `@Bean`, `@Value`, `@RequestBody`, `@RequestParam`, `@RequestPart`, `@PostMapping`, `@GetMapping`, `@CircuitBreaker`.

---

### B6. Spring MVC vs WebFlux

| Spring MVC | Spring WebFlux |
|-----------|----------------|
| Servlet (Tomcat), blocking | Netty, non-blocking |
| 1 thread per request | Few event-loop threads |
| Returns `T`, `ResponseEntity` | Returns `Mono<T>`, `Flux<T>` |
| Simple CRUD | Streaming, high concurrency |

**In my project**: I chose WebFlux because I stream LLM tokens (`Flux<String>`) to the browser in real-time.

---

### B7. What is Spring IoC?

**Answer**: Inversion of Control — the framework controls object creation and wiring, not your code. The IoC container (ApplicationContext) creates beans, injects dependencies, manages lifecycle.

**Example**: You don't write `new RagService()` — Spring creates it and hands it to whoever needs it.

---

# SECTION C: MICROSERVICES

---

### C1. Explain your project architecture

**Answer**: A microservices-based AI Contract Analyzer:
- **Frontend** (React) → **Backend** (Spring WebFlux, RAG orchestration)
- **Audit microservice** (separate Spring Boot) — logs events
- **PostgreSQL + pgvector** (vector storage), **Redis** (cache/rate limit), **Ollama** (LLM), **Kafka** (async events), **Zipkin** (tracing)

---

### C2. How do microservices communicate?

**Answer**: Two ways:
1. **Synchronous** — HTTP/REST (WebClient, Feign) — caller waits for response
2. **Asynchronous** — messaging (Kafka, RabbitMQ) — fire and forget

**In my project**:
- Upload audit → **synchronous HTTP** (WebClient) — need confirmation
- Chat audit → **asynchronous Kafka** — high throughput, decoupled

---

### C3. If one of 3 services fails, how do you communicate status to the next?

**Answer**: Several mechanisms:
1. **Circuit Breaker** — detects failure, returns fallback, stops cascading
2. **Kafka events** — publish a "FAILED" event; other services consume and react
3. **Saga pattern** — trigger compensation transactions to undo previous steps

**Example (Circuit Breaker in my project)**:
```java
@CircuitBreaker(name = "auditService", fallbackMethod = "auditFallback")
public Mono<String> logAudit(...) { ... }

private Mono<String> auditFallback(..., Throwable t) {
    return Mono.just("Audit service unavailable");  // graceful degradation
}
```

---

### C4. Why Microservices over Monolithic?

| Monolith | Microservices |
|----------|--------------|
| One big deployable | Independent services |
| Scale everything together | Scale each service independently |
| One tech stack | Polyglot (different tech per service) |
| One failure = whole app down | Isolated failures |

**My reason**: The audit service scales differently from the AI backend. I can deploy/scale them independently.

---

### C5. Microservices design patterns (know these)

| Pattern | Purpose |
|---------|---------|
| **API Gateway** | Single entry point, routing, auth |
| **Service Discovery** | Services find each other dynamically (Eureka) |
| **Circuit Breaker** | Prevent cascading failures (Resilience4j) |
| **Saga** | Distributed transactions across services |
| **CQRS** | Separate read and write models |

---

### C6. What is the Saga Pattern? Why needed?

**Answer**: In microservices, each service has its own DB — you can't use one ACID transaction across all. Saga breaks a distributed transaction into a sequence of local transactions, each with a **compensating transaction** to undo if something fails.

**Example (Order flow)**:
```
1. Order Service    → create order       (compensate: cancel order)
2. Payment Service  → charge card         (compensate: refund)
3. Inventory Service→ reserve stock       (compensate: release stock)

If step 3 fails → run compensations for steps 2 and 1 (refund + cancel)
```

---

### C7. Saga: Choreography vs Orchestration

| Choreography | Orchestration |
|-------------|---------------|
| No central controller | Central orchestrator |
| Services react to events | Orchestrator tells each service what to do |
| Event-driven (Kafka) | Command-driven |
| Loose coupling | Easier to track/debug |

**Example**:
- **Choreography**: Order Service publishes "OrderCreated" → Payment Service listens → publishes "PaymentDone" → Inventory listens...
- **Orchestration**: A Saga Orchestrator calls Order → Payment → Inventory in sequence, handling failures centrally.

---

### C8. What happens if one service fails mid-transaction?

**Answer**: Run **compensating transactions** in reverse order to undo completed steps. This achieves eventual consistency (not ACID, but the system self-heals).

---

### C9. How do you implement compensation transactions?

**Example**:
```java
try {
    orderService.create(order);
    paymentService.charge(order);
    inventoryService.reserve(order);
} catch (Exception e) {
    // Compensate in reverse
    paymentService.refund(order);
    orderService.cancel(order);
}
```

In event-driven: each service listens for a "compensate" event and undoes its work.

---

### C10. How do you prevent duplicate order creation?

**Answer**: **Idempotency Key** — client sends a unique key with each request. Server stores processed keys (in Redis). If the same key arrives again, return the cached result instead of creating a duplicate.

**Example**:
```java
public Order createOrder(String idempotencyKey, OrderRequest req) {
    if (redis.exists("order:" + idempotencyKey)) {
        return redis.get("order:" + idempotencyKey);   // duplicate — return existing
    }
    Order order = orderRepo.save(new Order(req));
    redis.set("order:" + idempotencyKey, order, Duration.ofHours(24));
    return order;
}
```

---

### C11. How to handle concurrent requests creating the same order?

**Answer**:
1. **Idempotency key** + Redis SETNX (set-if-not-exists — atomic)
2. **Optimistic locking** (version column)
3. **Unique DB constraint** as a last line of defense

**Example (Redis atomic)**:
```java
Boolean acquired = redis.opsForValue().setIfAbsent("lock:" + key, "1", Duration.ofSeconds(10));
if (!acquired) throw new DuplicateRequestException();
```

---

### C12. Feign Client vs WebClient

| Feign Client | WebClient |
|-------------|-----------|
| Declarative (interface + annotations) | Programmatic (fluent API) |
| Blocking (Spring MVC) | Non-blocking (WebFlux) |
| Simpler for simple calls | Full reactive control, streaming |
| Netflix OSS | Spring WebFlux native |

**Example**:
```java
// Feign — declarative
@FeignClient(name="audit", url="http://audit:8082")
interface AuditClient { @PostMapping("/log") String log(@RequestBody Event e); }

// WebClient — programmatic (my project)
webClient.post().uri("/api/audit/log").bodyValue(body).retrieve().bodyToMono(String.class);
```

**Why I chose WebClient**: My app is reactive (WebFlux). WebClient is non-blocking and fits the reactive stack. Feign is blocking.

---

### C13. How do you handle service unavailability?

**Answer**: Circuit Breaker + Retry + Fallback + Timeout (all via Resilience4j).
- **Timeout**: don't wait forever
- **Retry**: transient failures (with backoff)
- **Circuit Breaker**: stop calling a dead service
- **Fallback**: return default/cached response

---

### C14. How do you handle rate limiting?

**Answer**: Redis-based counter per user with TTL.

**Example (from my project)**:
```java
Long count = redis.opsForValue().increment("ratelimit:" + userId);
if (count == 1) redis.expire(key, Duration.ofMinutes(1));
if (count > 20) throw new RateLimitExceededException();
```

---

# SECTION D: EXCEPTION HANDLING

---

### D1. How do you handle runtime exceptions in microservices?

**Answer**: Use a global exception handler (`@RestControllerAdvice`) that catches exceptions and returns structured error responses. Combine with circuit breakers for downstream failures.

**Example**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handle(RuntimeException ex) {
        return ResponseEntity.status(500)
            .body(new ErrorResponse("INTERNAL_ERROR", ex.getMessage()));
    }
}
```

For WebFlux: implement `ErrorWebExceptionHandler`.

---

### D2. If the DB is not reachable, how are exceptions handled?

**Answer**:
1. **Connection pool timeout** (HikariCP) → throws `CannotGetJdbcConnectionException`
2. **Global handler** catches it → returns HTTP 503 (Service Unavailable)
3. **Circuit breaker** → after repeated failures, stops trying, returns cached/fallback
4. **Retry** → for transient blips

**Example**:
```java
@ExceptionHandler(DataAccessException.class)
public ResponseEntity<String> handleDbDown(DataAccessException ex) {
    return ResponseEntity.status(503).body("Database temporarily unavailable");
}
```

---

# SECTION E: DATABASE & TRANSACTIONS

---

### E1. Which database and why?

**Answer**: PostgreSQL 16 with the pgvector extension. Chosen because it stores both relational data AND vector embeddings, with HNSW indexing for fast semantic search — no need for a separate vector DB.

---

### E2. How do you optimize DB performance?

**Answer**:
- **Indexes** (HNSW for vectors, GIN for JSONB, B-tree for lookups)
- **Connection pooling** (HikariCP)
- **Query optimization** (LIMIT, avoid N+1)
- **Caching** (Redis for repeat reads)
- **Pagination** for large result sets

---

### E3. How do you handle transactions?

**Answer**: `@Transactional` for single-service ACID. For distributed transactions across microservices → Saga pattern.

**Example**:
```java
@Transactional
public void transferMoney(Account from, Account to, double amt) {
    from.debit(amt);
    to.credit(amt);
    // If any line throws, BOTH rollback
}
```

---

### E4. What is optimistic locking? Where is it used?

**Answer**: Optimistic locking assumes conflicts are rare. It uses a `@Version` column. When updating, it checks the version — if changed by another transaction, it fails with `OptimisticLockException`.

**Example**:
```java
@Entity
public class Employee {
    @Id private Long id;
    @Version private int version;   // auto-incremented on each update
    private String designation;
}
// If two users update the same employee, the second gets an exception → retry
```

**Used for**: high-read, low-write scenarios (avoids DB locks).

---

### E5. How do you handle high-volume reads and writes?

**Answer**:
- **Reads**: Redis caching, read replicas, CQRS (separate read model)
- **Writes**: async processing (Kafka), batching, write-behind cache, sharding

---

# SECTION F: REDIS & KAFKA (Project-Specific)

---

### F1. Why Redis? What advantages?

**Answer**: In-memory store for caching, rate limiting, and session/chat history. Advantages: sub-millisecond speed, TTL auto-expiry, atomic operations, shared across pods.

### F2. If data is in DB, why Redis?

**Answer**: DB reads are slow (disk + network + query). Redis serves frequent/repeat reads from RAM in ~1ms. In my project, repeat questions return cached LLM answers in 2ms vs 5-10 seconds.

### F3. What data / what format in Redis?

**Answer**:
- **Cached LLM responses** → String (JSON value)
- **Rate limit counters** → String (integer via INCR)
- **Chat history** → List (JSON per message)

### F4. How do you ensure cache consistency with DB updates?

**Answer**:
1. **TTL** — cache auto-expires (eventual consistency)
2. **Active invalidation** — on DB update, delete relevant cache keys

**Example (my project)**: On new PDF upload → `cacheService.invalidateContractCache(contractId)` deletes stale answers.

### F5. Purpose of Kafka? Real-time use case?

**Answer**: Async, decoupled event streaming. In my project, chat Q&A events are published to `contract-audit-topic` — the audit service consumes them independently without slowing the user's response.

### F6. How do you achieve reliable event processing?

**Answer**: Kafka guarantees via acknowledgments (acks=all), consumer offset commits, idempotent producers, and dead-letter topics for failed messages.

---

# SECTION G: HTTP & REST

---

### G1. HTTP PUT vs PATCH

| PUT | PATCH |
|-----|-------|
| Replaces the ENTIRE resource | Updates PART of the resource |
| Idempotent | Can be non-idempotent |
| Send full object | Send only changed fields |

**Example**:
```
PUT /employees/1     { "id":1, "name":"Riaan", "designation":"Lead", "salary":60000 }  (full)
PATCH /employees/1   { "designation":"Lead" }   (only the field to change)
```

---

### G2. Explain "update employee's designation" flow

**Answer** (typical microservice flow):
```
1. Client → PATCH /employees/1 {"designation":"Senior Engineer"}
2. Controller receives request → validates input
3. Service layer: fetch employee (with @Version for optimistic lock)
4. Update the designation field
5. Repository saves → @Transactional commits
6. If concurrent update → OptimisticLockException → retry or 409 Conflict
7. Publish "EmployeeUpdated" event to Kafka (other services sync)
8. Return 200 OK with updated employee
9. Invalidate any Redis cache for this employee
```

---

# SECTION H: CI/CD & DEPLOYMENT

---

### H1. How are deployments performed? Pipeline from commit to production?

**Answer** (typical):
```
1. Developer commits → Git push
2. CI (GitHub Actions/Jenkins) triggers:
   - Build (Maven)
   - Run unit + integration tests
   - Build Docker image
   - Push image to registry (GCR/ECR)
3. CD deploys:
   - Pull image on server / Kubernetes
   - Rolling update (zero downtime)
   - Health check
4. If health check fails → auto-rollback
```

**In my project**: Terraform provisions GCP infra; Docker Compose builds and runs containers.

### H2. CI/CD tools

GitHub Actions / Jenkins (CI), Docker (containerization), Terraform (IaC), Kubernetes (orchestration).

### H3. How do you handle rollback on failure?

**Answer**: Kubernetes `kubectl rollout undo` reverts to the previous ReplicaSet. Blue-green or canary deployments allow instant switch-back. Keep previous Docker image tagged.

### H4. How do you ensure zero-downtime deployments?

**Answer**: Rolling updates (start new pods, verify healthy, then kill old) with readiness probes. `maxUnavailable: 0` ensures always-available pods. Blue-green for instant cutover.

---

# SECTION I: CONCURRENCY

---

### I1. Thread pools

**Answer**: A pool of reusable threads. Instead of creating a thread per task (expensive), tasks are submitted to a pool.

**Example**:
```java
ExecutorService pool = Executors.newFixedThreadPool(10);
pool.submit(() -> doWork());
```
In WebFlux, I use `Schedulers.boundedElastic()` for blocking work.

### I2. Synchronization

**Answer**: Prevents multiple threads from corrupting shared data. `synchronized` keyword or locks ensure only one thread accesses a critical section at a time.

**Example**:
```java
public synchronized void increment() { count++; }  // thread-safe
```

### I3. Locking mechanism

**Answer**:
- **Optimistic** (version-based, no DB lock) — high concurrency
- **Pessimistic** (`SELECT ... FOR UPDATE`, DB lock) — high contention
- **Distributed lock** (Redis SETNX) — across microservices

---

# SECTION J: SECURITY

---

### J1. Recent security vulnerability you fixed?

**Answer** (from my project): Secrets (DB passwords) were hardcoded in config files, exposed in Git. I fixed it by integrating **GCP Secret Manager** — secrets are now fetched at runtime via `${sm://secret-name}`, never stored in code. Access is controlled via IAM roles.

### J2. Spring Security basics

**Answer**: Handles authentication (who you are) and authorization (what you can do). Common: JWT tokens, role-based access, filter chains.

---

# QUICK-FIRE SUMMARY TABLE

| Topic | One-liner |
|-------|-----------|
| record | Immutable data class, auto boilerplate |
| LinkedHashSet | Order + no duplicates + fast |
| final | Cannot change/override/extend |
| Functional interface | One abstract method, target for lambda |
| CompletableFuture | Non-blocking async with chaining |
| @Bean vs @Component | Third-party vs your own class |
| WebFlux | Reactive, non-blocking, Mono/Flux |
| Saga | Distributed transaction with compensations |
| Circuit Breaker | Stop calling failed service, fallback |
| Idempotency Key | Prevent duplicate operations |
| Optimistic Locking | @Version, fails on concurrent update |
| PUT vs PATCH | Full replace vs partial update |
| Redis | Fast in-memory cache/rate-limit |
| Kafka | Async decoupled event streaming |

---

# SECTION K: EXTRA DEEP-DIVE TOPICS

---

### K1. Different ways microservices communicate (full list)

| Type | Technology | When |
|------|-----------|------|
| Synchronous REST | RestTemplate, WebClient, Feign | Need immediate response |
| Async messaging | Kafka, RabbitMQ | Decoupled, high throughput |
| gRPC | Protocol Buffers | Fast, typed, internal services |
| Service mesh | Istio | Advanced routing, observability |

---

### K2. API Gateway pattern

**Answer**: A single entry point for all clients. Handles routing, authentication, rate limiting, and request aggregation. Clients don't call each microservice directly.

**Example**: Spring Cloud Gateway routes `/api/orders/*` → Order Service, `/api/payments/*` → Payment Service. Adds JWT validation once, centrally.

---

### K3. Service Discovery

**Answer**: Services register themselves with a registry (Eureka, Consul). Others look up the registry to find a service's current location — no hardcoded IPs. Essential when services scale up/down dynamically.

**Example**: Order Service asks Eureka "where is Payment Service?" → gets a healthy instance's address.

---

### K4. CQRS (Command Query Responsibility Segregation)

**Answer**: Separate the write model (Commands) from the read model (Queries). Writes go to one DB, reads from an optimized read store (often synced via events).

**Example**:
```
Command: CreateOrder → writes to Order DB → publishes event
Query:   GetOrderHistory → reads from a denormalized read DB (fast)
```
Benefit: scale reads and writes independently.

---

### K5. How do Redis and DB together help duplicate validation?

**Answer**:
1. **Redis (first line)** — fast atomic check with `SETNX` on the idempotency key. Instant rejection of duplicates.
2. **DB (last line)** — unique constraint on a business key (e.g., order_number). Even if Redis fails, DB rejects duplicates.

**Example**:
```java
// Redis fast check
if (!redis.setIfAbsent("order:" + key, "1", TTL)) return existing;
// DB guarantee (unique constraint on order_number)
try { orderRepo.save(order); }
catch (DataIntegrityViolationException e) { return findExisting(order); }
```

---

### K6. How do you validate duplicate requests from users?

**Answer**: Client sends an **Idempotency-Key** header (a UUID). Server checks Redis: if the key was seen → return the previous response; otherwise process and store the key. This makes retries safe.

---

# SECTION L: JAVA 8 STREAMS CODING PRACTICE

---

### L1. Common stream operations with examples

```java
List<Employee> emps = ...;

// 1. Filter high earners
emps.stream().filter(e -> e.salary() > 50000).collect(toList());

// 2. Get all names
emps.stream().map(Employee::name).collect(toList());

// 3. Group by department
emps.stream().collect(groupingBy(Employee::dept));

// 4. Average salary per dept
emps.stream().collect(groupingBy(Employee::dept, averagingDouble(Employee::salary)));

// 5. Total salary
emps.stream().mapToDouble(Employee::salary).sum();

// 6. Count by dept
emps.stream().collect(groupingBy(Employee::dept, counting()));

// 7. Sort by salary descending
emps.stream().sorted(comparingDouble(Employee::salary).reversed()).collect(toList());

// 8. Highest paid employee
emps.stream().max(comparingDouble(Employee::salary));

// 9. Names joined by comma
emps.stream().map(Employee::name).collect(joining(", "));

// 10. Partition (pass/fail)
emps.stream().collect(partitioningBy(e -> e.salary() > 50000));
```

---

### L2. Lambda expression basics

**Answer**: A lambda is a short anonymous function. Syntax: `(parameters) -> body`.

**Example**:
```java
// Old anonymous class
Runnable r1 = new Runnable() {
    public void run() { System.out.println("Hi"); }
};
// Lambda
Runnable r2 = () -> System.out.println("Hi");

// With Comparator
list.sort((a, b) -> a.compareTo(b));
```

---

### L3. Find frequency of each word (Streams)

```java
List<String> words = List.of("a", "b", "a", "c", "b", "a");
Map<String, Long> freq = words.stream()
    .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
// {a=3, b=2, c=1}
```

---

### L4. Find first non-repeating character (Streams)

```java
String s = "swiss";
Character result = s.chars()
    .mapToObj(c -> (char) c)
    .collect(Collectors.groupingBy(c -> c, LinkedHashMap::new, Collectors.counting()))
    .entrySet().stream()
    .filter(e -> e.getValue() == 1)
    .map(Map.Entry::getKey)
    .findFirst().orElse(null);
// 'w'
```

---

*Good luck with your interview!*
