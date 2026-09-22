# Java and Spring Backend Interview Q&A
## 300 Questions with Practical Production Examples

Examples use the Contract Analyzer system: Java 21, Spring Boot WebFlux, Spring AI, PostgreSQL/pgvector, Redis, Docker, and Kubernetes.

## Java Fundamentals and Concurrency

### 1. What is the difference between JDK, JRE, and JVM?
**Answer:** The JVM runs bytecode, the JRE adds libraries, and the JDK adds development tools such as `javac`. Example: the backend container uses a JDK image to compile the service, then a smaller JRE image to run the jar.

### 2. How does the Java memory model work and what is the happens-before relationship?
**Answer:** The model defines how threads see shared memory; happens-before guarantees visibility and ordering. Example: unlocking a synchronized block happens-before another thread locks it, so a newly uploaded contract ID is visible.

### 3. What are the main garbage collectors available in modern JVMs and when would you choose each?
**Answer:** Serial suits small heaps, Parallel favors throughput, G1 balances throughput and pauses, ZGC targets very large heaps and short pauses, and Shenandoah also minimizes pauses. Example: choose G1 for a normal API and ZGC for a huge low-latency service.

### 4. How does generational garbage collection work?
**Answer:** New objects start in a young generation; surviving objects move to older generations. Example: temporary JSON request objects die in a young collection while long-lived Spring beans remain old-generation objects.

### 5. What is escape analysis and how can it improve performance?
**Answer:** The JVM checks whether an object escapes a method or thread and may allocate it on the stack or eliminate it. Example: a temporary value object inside cache-key creation may require no heap allocation.

### 6. Explain class loading in Java and the role of the bootstrap, extension, and application class loaders.
**Answer:** Class loaders load bytecode, normally delegating first to the parent. The bootstrap loads Java core classes, platform loading replaces the old extension loader, and the application loader loads application dependencies. Example: Spring classes are loaded by the application loader.

### 7. What is the difference between checked and unchecked exceptions?
**Answer:** Checked exceptions must be caught or declared; unchecked exceptions extend `RuntimeException` and usually indicate programming or validation failures. Example: a file reader may declare `IOException`, while an invalid request can throw `IllegalArgumentException`.

### 8. When should you use custom exceptions and how do you design them?
**Answer:** Use domain-specific exceptions when callers need distinct handling; include a useful message, cause, and stable error code. Example: `ContractNotFoundException` maps to HTTP 404 while `EmbeddingServiceException` maps to a retryable 503.

### 9. What are final, finally, and finalize and how do they differ?
**Answer:** `final` prevents reassignment or overriding, `finally` runs cleanup code, and `finalize` is obsolete and should not be used. Example: a `final` Redis client is closed in a lifecycle method, not via `finalize`.

### 10. How does Java handle memory leaks and what are common causes in backend applications?
**Answer:** Garbage collection cannot reclaim reachable objects, so leaks come from unbounded caches, static collections, listeners, or unclosed resources. Example: storing every chat response in a map without TTL eventually exhausts heap memory.

### 11. What is the difference between String, StringBuilder, and StringBuffer?
**Answer:** `String` is immutable, `StringBuilder` is mutable and unsynchronized, and `StringBuffer` is synchronized but slower. Example: collect streamed LLM tokens with one request-local `StringBuilder`.

### 12. How do you implement immutability in Java classes?
**Answer:** Make the class and fields final, initialize fields once, avoid setters, and defensively copy mutable inputs and outputs. Example: a chat request record prevents accidental mutation after validation.

### 13. What are records in Java and when should you use them?
**Answer:** Records concisely model immutable data carriers with generated accessors, equality, and constructors. Example: `record ChatRequest(Long contractId, Long userId, String question)` represents an HTTP payload.

### 14. Explain Java 8 streams and how they differ from traditional loops.
**Answer:** Streams express a pipeline of transformations and terminal operations, often lazily; loops expose control flow directly. Example: filter retrieved chunks by contract ID, map content, and collect prompt text.

### 15. What are lambda expressions and functional interfaces?
**Answer:** A lambda is behavior passed as a value; a functional interface has one abstract method. Example: `chunks.forEach(chunk -> log.info(chunk.content()))` supplies behavior without a helper class.

### 16. How does Optional work and what are best practices for its use?
**Answer:** `Optional` models a possibly absent value; use it for return values, not entity fields or method parameters, and avoid `get()`. Example: `repository.findById(id).orElseThrow(...)` handles missing contracts explicitly.

### 17. What is method reference syntax and when is it useful?
**Answer:** Method references such as `String::trim` are shorter lambdas when an existing method matches the functional signature. Example: normalize question strings with `questions.stream().map(String::trim)`.

### 18. Explain the difference between map, flatMap, and filter in streams.
**Answer:** `map` transforms one item, `flatMap` transforms and flattens nested results, and `filter` keeps matching items. Example: map documents to chunks, flatMap pages into words, and filter empty chunks.

### 19. How do you parallelize stream operations and what are the pitfalls?
**Answer:** `parallelStream()` uses the common ForkJoin pool, but shared mutable state, blocking I/O, ordering, and small workloads can make it slower or unsafe. Do not parallelize HTTP calls casually in the chat path.

### 20. What is the ForkJoin framework and when should you use it?
**Answer:** ForkJoin recursively splits CPU-bound work and steals tasks between worker threads. Example: parallel document text analysis can use ForkJoin, but blocking database calls should use an appropriate executor.

### 21. How do CompletableFuture and Future differ and when to use each?
**Answer:** `Future` supports waiting and cancellation; `CompletableFuture` composes asynchronous stages and error handling. Example: retrieve embeddings and metadata concurrently, then combine them before generating an answer.

### 22. What is reactive programming and how does it compare to imperative programming?
**Answer:** Reactive programming represents asynchronous streams with non-blocking composition; imperative code waits and proceeds step by step. Example: WebFlux streams LLM tokens without occupying a thread while waiting for Ollama.

### 23. What are the core concepts of Project Reactor or RxJava?
**Answer:** Reactor uses `Mono` for zero-or-one values and `Flux` for many values, with lazy publishers, operators, subscribers, and schedulers. Example: `/api/chat/stream` returns a `Flux<String>`.

### 24. How do you handle backpressure in reactive systems?
**Answer:** Backpressure lets consumers control production using buffering, dropping, limiting, or requesting demand. Example: an SSE client that reads slowly must not cause unlimited LLM tokens to accumulate in server memory.

### 25. Explain the Java concurrency utilities in java.util.concurrent.
**Answer:** They provide executors, futures, locks, atomics, concurrent collections, semaphores, latches, and queues. Example: a `BlockingQueue` can separate PDF ingestion from embedding workers.

### 26. What is the difference between synchronized and ReentrantLock?
**Answer:** `synchronized` is simpler and automatically releases; `ReentrantLock` supports timed acquisition, interruption, fairness, and multiple conditions. Use the simpler option unless advanced lock control is needed.

### 27. How does volatile work and when should you use it?
**Answer:** `volatile` guarantees visibility and prevents certain reorderings but does not make compound operations atomic. Example: a shutdown flag can be volatile; an incrementing request counter needs an atomic class.

### 28. What are atomic classes and how do they help with concurrency?
**Answer:** Atomic classes use lock-free compare-and-set operations for single-variable updates. Example: `AtomicInteger` counts in-flight uploads safely across request threads.

### 29. Explain the ABA problem and how to mitigate it.
**Answer:** A value changes A→B→A, so a compare-and-set sees A and misses the intermediate change. Use version stamps such as `AtomicStampedReference`; database optimistic locking uses the same version idea.

### 30. How do you detect and resolve deadlocks in a Java application?
**Answer:** Capture a thread dump with `jstack` or JFR and look for threads waiting cyclically on monitors. Fix lock ordering, shorten critical sections, or use timed locks; never “solve” it by restarting repeatedly.

### 31. What is thread starvation and how can it be prevented?
**Answer:** Starvation occurs when a thread never gets CPU or a lock. Use bounded pools, fair locks where appropriate, short tasks, and avoid blocking the WebFlux event loop with JDBC or file operations.

### 32. How do Executors and ThreadPools work and how do you size them?
**Answer:** Executors reuse worker threads and queue tasks. CPU-bound pools are near core count; I/O-bound pools may be larger but must be bounded. Example: isolate blocking PDF parsing from request processing.

### 33. What is the difference between cached, fixed, and scheduled thread pools?
**Answer:** Cached pools grow and reuse threads, fixed pools cap concurrency, and scheduled pools run delayed or periodic tasks. A fixed pool is safer for controlled contract ingestion; scheduled pools suit cleanup jobs.

### 34. How does ConcurrentHashMap work internally?
**Answer:** It permits concurrent reads and updates using fine-grained coordination and CAS/bin locking rather than one global lock. Example: maintain a thread-safe per-user request state map without blocking all users.

### 35. What are concurrent collections and when should you use them?
**Answer:** They include `ConcurrentHashMap`, `CopyOnWriteArrayList`, blocking queues, and concurrent sets. Choose based on access pattern; a blocking queue is better for producer-consumer work than a synchronized list.

### 36. Explain the Producer-Consumer pattern and how to implement it in Java.
**Answer:** Producers enqueue work and consumers process it at their own rate, commonly using `BlockingQueue`. Example: upload requests produce PDF jobs while embedding workers consume them.

### 37. What is the difference between wait/notify and Lock/Condition?
**Answer:** `wait/notify` belongs to an object's monitor; `Condition` provides multiple explicit wait sets with `Lock`. Prefer higher-level queues when possible because they encode correct coordination.

### 38. How do you implement a thread-safe singleton in Java?
**Answer:** Prefer a Spring singleton bean, or use an enum or initialization-on-demand holder. Example: Spring creates one `CacheService` bean and safely injects it into controllers.

### 39. What is the Java Memory Model guarantee for final fields?
**Answer:** Properly constructed objects provide stronger visibility guarantees for final fields after construction. Do not leak `this` from a constructor, and keep referenced mutable objects protected.

### 40. How does class initialization work and what are static initializers?
**Answer:** A class initializes when first actively used; static fields and blocks run once, safely under JVM synchronization. Example: a static cache prefix is initialized before the first service call.

## Reflection, Spring, and Boot

### 41. What is reflection and what are its performance/security implications?
**Answer:** Reflection inspects and invokes classes at runtime, enabling frameworks but adding overhead and bypass risks. Spring uses it for dependency injection; do not expose arbitrary class names from user input.

### 42. How do you use annotations and how are they processed at runtime and compile time?
**Answer:** Annotations attach metadata; runtime annotations can be inspected by reflection, while compile-time processors generate or validate code. `@RestController` is read by Spring at runtime; Lombok acts mainly at compile time.

### 43. What is dependency injection and inversion of control?
**Answer:** DI supplies dependencies from outside a class; IoC means the framework controls object creation and lifecycle. A controller receives `RagService` instead of constructing it with `new`.

### 44. How does Spring implement dependency injection?
**Answer:** The application context scans configuration and components, creates beans, resolves dependencies, and injects them through constructors, setters, or fields. Constructor injection is easiest to test and makes dependencies explicit.

### 45. What are the differences between @Component, @Service, @Repository, and @Controller?
**Answer:** All register beans; `@Service` communicates business logic, `@Repository` marks persistence and exception translation, and `@Controller` handles web requests. `@RestController` additionally serializes return values as response bodies.

### 46. How does Spring Boot simplify Spring application setup?
**Answer:** Boot provides starters, auto-configuration, embedded servers, external configuration, and production features. Adding WebFlux and a Redis starter can configure most infrastructure without XML.

### 47. What is auto-configuration in Spring Boot and how does it work?
**Answer:** Boot conditionally creates beans based on classpath libraries, properties, and missing beans. Adding a PostgreSQL driver and datasource properties causes a pooled `DataSource` to be configured.

### 48. How do you create custom Spring Boot starters?
**Answer:** Package reusable auto-configuration, properties, conditional annotations, and an auto-configuration import entry. A company could publish a starter that standardizes tracing and correlation IDs in every service.

### 49. Explain the Spring Bean lifecycle and scopes.
**Answer:** Spring instantiates, injects, post-processes, initializes, uses, and destroys beans. Singleton is one context instance, prototype creates a new instance per request, and web scopes follow HTTP lifetimes.

### 50. What is the difference between prototype and singleton bean scopes?
**Answer:** A singleton is shared and must be thread-safe; a prototype is created each time it is requested and Spring does not fully manage its later destruction. Stateless services normally use singleton scope.

### 51. How does Spring handle circular dependencies?
**Answer:** Constructor cycles generally fail fast; setter or field cycles may sometimes be resolved through early references, but redesign is preferable. Split responsibilities or introduce a coordinating service instead of relying on `@Lazy`.

### 52. What are Spring profiles and how do you use them?
**Answer:** Profiles activate environment-specific beans and configuration. Use `dev` for local Ollama and `prod` for managed services, selected with `SPRING_PROFILES_ACTIVE=prod`.

### 53. How do you externalize configuration in Spring Boot?
**Answer:** Use YAML/properties, environment variables, command-line arguments, ConfigMaps, and secret stores. Database passwords should come from Kubernetes Secrets, not committed source files.

### 54. What is Spring Data JPA and how does it simplify data access?
**Answer:** It generates repository implementations from interfaces and method names, reducing CRUD boilerplate. `ContractRepository.findByUserId` can become a query without handwritten JDBC.

### 55. Explain the JPA entity lifecycle and persistence context.
**Answer:** Entities are transient, managed, detached, or removed; the persistence context tracks managed objects and flushes changes. A transaction loads a contract, changes its filename, and flushes the update on commit.

### 56. What is the difference between EntityManager and Session (Hibernate)?
**Answer:** `EntityManager` is the standard JPA API; Hibernate `Session` is its vendor-specific richer API. Use EntityManager for portability and Session-specific features only when justified.

### 57. How do you map one-to-one, one-to-many, and many-to-many relationships in JPA?
**Answer:** Use relationship annotations and explicit ownership/join columns. A contract may have many chunks; map the foreign key carefully and avoid eager-loading thousands of chunks.

### 58. What is lazy vs eager loading and what problems can lazy loading cause?
**Answer:** Lazy loads related data on access; eager loads immediately. Lazy access outside a transaction can fail, while eager relationships can create huge queries; fetch only what the endpoint needs.

### 59. How do you solve the N+1 query problem?
**Answer:** Use fetch joins, entity graphs, batch fetching, or projections. A contract list endpoint should fetch required owner data in one query instead of querying the owner for every contract.

### 60. What are JPQL and Criteria API and when to use each?
**Answer:** JPQL is readable object-oriented query text; Criteria is programmatic and useful for dynamic filters but verbose. Use JPQL for stable contract searches and Criteria for optional search fields.

### 61. How do transactions work in Spring and what is @Transactional doing?
**Answer:** Spring wraps the method in a transaction proxy, committing on success and rolling back according to rules. An upload transaction can save contract metadata and chunk records atomically.

### 62. What are transaction propagation behaviors and examples of each?
**Answer:** `REQUIRED` joins or creates, `REQUIRES_NEW` suspends and creates, `MANDATORY` requires one, and `NOT_SUPPORTED` suspends. Audit logging may use `REQUIRES_NEW` so it persists even if business work fails.

### 63. Explain isolation levels and their tradeoffs (READ_UNCOMMITTED to SERIALIZABLE).
**Answer:** Higher isolation prevents more anomalies but reduces concurrency. Normal reads may use READ_COMMITTED; a scarce resource allocation may require SERIALIZABLE or explicit locking.

### 64. What are dirty reads, nonrepeatable reads, and phantom reads?
**Answer:** A dirty read sees uncommitted data, a nonrepeatable read sees a changed row, and a phantom read sees new matching rows. Isolation levels trade these anomalies against throughput.

### 65. How do optimistic and pessimistic locking differ?
**Answer:** Optimistic locking checks a version at update time; pessimistic locking acquires a database lock early. Contract metadata edited rarely favors optimistic locking; a highly contended quota may use pessimistic locking.

### 66. What is a distributed transaction and what are common patterns to handle them?
**Answer:** It spans multiple independent resources or services. Prefer local transactions plus outbox, Saga, idempotency, and compensation instead of coordinating every service with one global transaction.

### 67. Explain two-phase commit and its drawbacks.
**Answer:** A coordinator asks participants to prepare, then commits all or rolls back all. It holds resources, adds latency, and can block during coordinator failure, so it is uncommon for loosely coupled microservices.

### 68. What is the Saga pattern and when should you use it?
**Answer:** A Saga breaks a workflow into local transactions with compensating actions, orchestrated or event-driven. A failed payment after contract creation can trigger a compensation that marks the contract processing as cancelled.

### 69. How do you design idempotent APIs and why is idempotency important?
**Answer:** Repeating the same request produces the same effect, often using an idempotency key stored with the result. A client retrying PDF upload should not create duplicate indexing jobs.

### 70. What is eventual consistency and how do you design systems around it?
**Answer:** Replicas converge after a delay rather than immediately. After uploading a document, show “processing” until embeddings are indexed instead of promising immediate search visibility.

### 71. Explain the CAP theorem and its implications for distributed systems.
**Answer:** During a network partition, a distributed system must choose consistency or availability; partition tolerance is unavoidable. A chat cache may favor availability, while payment state favors consistency.

### 72. What is BASE and how does it contrast with ACID?
**Answer:** BASE means basically available, soft state, and eventual consistency; ACID provides transactional guarantees. Redis-backed chat history can be BASE-like, while PostgreSQL contract metadata uses ACID transactions.

### 73. How do you implement caching in a Java backend and what strategies exist?
**Answer:** Cache expensive results with a bounded TTL, clear ownership, and invalidation rules using Caffeine, Redis, or a provider. Cache repeated contract questions in Redis to avoid repeated LLM work.

### 74. What is cache aside, write-through, write-behind, and read-through?
**Answer:** Cache-aside loads on misses, write-through writes cache and store together, write-behind delays store writes, and read-through lets the cache provider load data. The analyzer uses cache-aside for generated answers.

### 75. How do you handle cache invalidation and cache coherence in distributed caches?
**Answer:** Use versioned keys, TTLs, events, or explicit deletes and define stale-data tolerance. Invalidate all contract response keys after a replacement PDF is indexed.

### 76. What is Redis and how is it commonly used in backend systems?
**Answer:** Redis is an in-memory data store supporting strings, lists, sets, hashes, streams, TTLs, and atomic commands. This application uses it for response caching, rate limits, and chat history.

### 77. How do you implement distributed locks with Redis?
**Answer:** Acquire a unique token with `SET key token NX PX ttl`, perform work, and release only if the token still matches using a script. Use short leases and handle expiry; never delete another worker's lock.

### 78. What are the tradeoffs of using in-memory caches vs distributed caches?
**Answer:** Local caches are fastest but isolated per instance and lost on restart; distributed caches share state but add network and operational cost. Use Caffeine for local metadata and Redis for cross-pod rate limits.

### 79. How do you design a cache key strategy to avoid collisions?
**Answer:** Include a namespace, tenant/user, resource identity, version, and normalized input; hash long content. Example: `rag:response:{contractId}:{userId}:{questionHash}` isolates answers correctly.

### 80. What is a CDN and when should you use one for backend assets?
**Answer:** A CDN caches content near users, reducing latency and origin load. Serve the React static bundle through a CDN, but keep authenticated chat responses at the API origin.

## APIs, Security, and Operations

### 81. Explain REST principles and what makes an API RESTful.
**Answer:** REST uses resource-oriented URLs, stateless requests, representations, standard HTTP methods, and cache semantics. `POST /api/upload` creates processing work and `GET /api/chat/history` retrieves a resource.

### 82. What is HATEOAS and is it necessary for REST APIs?
**Answer:** HATEOAS places navigable links in responses so clients discover actions. It is useful for public evolving APIs but not mandatory for a focused internal contract analyzer.

### 83. How do you version REST APIs and what are best practices?
**Answer:** Use URL, header, or media-type versioning; preserve old contracts and document deprecation. `/api/v1/chat` can coexist with `/api/v2/chat` while clients migrate.

### 84. What is GraphQL and how does it compare to REST?
**Answer:** GraphQL lets clients request a precise nested shape from one endpoint; REST uses resource endpoints and server-defined representations. GraphQL suits varied frontend views, while REST is simpler to cache and secure.

### 85. When should you use gRPC instead of REST?
**Answer:** Choose gRPC for efficient internal typed calls, streaming, and generated clients; choose REST for browser-facing interoperability. An embedding worker could call an internal gRPC service while the browser uses REST.

### 86. How do you design pagination for APIs and what are cursor vs offset approaches?
**Answer:** Offset is simple but slows and shifts under changes; cursor pagination is stable and efficient for large ordered data. Chat history can use a cursor based on message ID or timestamp.

### 87. What is rate limiting and how do you implement it?
**Answer:** Rate limiting caps request frequency using fixed windows, sliding windows, token buckets, or leaky buckets. Redis `INCR` plus TTL can limit each user to 20 questions per minute.

### 88. What is circuit breaker pattern and how does it improve resilience?
**Answer:** A breaker opens after repeated failures, rejects calls quickly, then probes recovery. If Ollama is down, return a clear temporary error instead of exhausting backend threads.

### 89. What is bulkhead isolation and why is it useful?
**Answer:** Bulkheads allocate separate resources so one workload cannot consume everything. Give PDF ingestion its own bounded executor so large uploads do not starve chat requests.

### 90. How do you implement retries with exponential backoff?
**Answer:** Retry transient failures with increasing delays and jitter, bounded attempts, and only for safe/idempotent operations. Retry an Ollama timeout twice, but do not retry invalid PDF input.

### 91. What is idempotency key and how is it used in HTTP APIs?
**Answer:** A client sends a unique key for one logical operation; the server stores its result and returns it for repeats. Payment and upload endpoints use keys to make network retries safe.

### 92. How do you secure REST APIs (authentication and authorization)?
**Answer:** Authenticate identity with OAuth2/OIDC or mTLS, authorize each resource and action, validate input, use TLS, and log safely. A user must not query another user's contract by changing `user_id`.

### 93. What is OAuth2 and how does it differ from OpenID Connect?
**Answer:** OAuth2 delegates authorization to access resources; OIDC adds authentication and identity claims on top of OAuth2. Use OIDC login for users and OAuth2 client credentials for service-to-service calls.

### 94. How do JWTs work and what are common security pitfalls?
**Answer:** A signed JWT carries claims that a server verifies without a session lookup. Validate signature, issuer, audience, expiry, algorithm, and never put secrets or sensitive contract text in the payload.

### 95. What is CSRF and how do you prevent it?
**Answer:** CSRF tricks a browser into sending an authenticated state-changing request. SameSite cookies, CSRF tokens, origin checks, and stateless bearer headers reduce risk; cookie-based browser sessions need special care.

### 96. What is CORS and how do you configure it safely?
**Answer:** CORS controls which browser origins may call an API. Allow only known frontend origins and methods, never `*` with credentials; the analyzer's WebFlux CORS filter should use deployment configuration.

### 97. How do you store and manage secrets in a backend application?
**Answer:** Store secrets in a secret manager or Kubernetes Secret, inject at runtime, rotate them, and restrict access. PostgreSQL and Redis passwords must not live in Git or logs.

### 98. What are common web security headers and their purposes?
**Answer:** CSP limits executable sources, HSTS enforces HTTPS, frame-ancestors prevents clickjacking, and content-type options reduce sniffing. Configure them at the gateway or Spring Security layer.

### 99. How do you prevent SQL injection and other injection attacks?
**Answer:** Use prepared statements, parameter binding, allowlists, output encoding, and safe parsers. Never concatenate a user question or filename into SQL; use `JdbcTemplate` parameters or JPA bindings.

### 100. What is input validation vs output encoding and when to use each?
**Answer:** Validation rejects malformed or dangerous input at boundaries; encoding makes output safe in its destination context. Validate upload type and size, then HTML-encode user chat text rendered in the browser.

### 101. How do you implement role-based access control (RBAC) in a Java app?
**Answer:** Assign roles and enforce permissions at endpoints and service/resource boundaries. An `ADMIN` may delete contracts, while an `ANALYST` can query only owned contracts.

### 102. What is attribute-based access control (ABAC) and when is it useful?
**Answer:** ABAC evaluates attributes of subject, resource, action, and environment. Allow a legal team member to read a contract only when `department`, `tenant`, and classification rules match.

### 103. How do you perform authentication in microservices architecture?
**Answer:** Validate signed tokens at the gateway and/or each service, propagate identity and authorization context, and use service identities internally. Do not trust a user ID supplied only in a JSON body.

### 104. What is mutual TLS and when should you use it?
**Answer:** Both client and server present certificates, authenticating each other and encrypting traffic. Use mTLS between backend, embedding, and database services in a zero-trust cluster.

### 105. How do you implement single sign-on (SSO) for backend services?
**Answer:** Use a central OIDC identity provider; applications redirect users for login and validate returned tokens. Multiple company tools then share identity without sharing passwords.

### 106. What is OAuth2 client credentials flow and when to use it?
**Answer:** A confidential service authenticates with its client credentials to obtain an access token without a user. An ingestion worker uses it to call the contract API.

### 107. How do you handle session management in stateless services?
**Answer:** Keep sessions in signed tokens or shared storage such as Redis, and avoid local instance memory. Shared Redis sessions allow requests routed to any Kubernetes replica.

### 108. What are secure cookie attributes and why are they important?
**Answer:** `Secure` requires HTTPS, `HttpOnly` blocks JavaScript access, and `SameSite` limits cross-site sending. These reduce token theft and CSRF when browser sessions are used.

### 109. How do you log securely without leaking sensitive data?
**Answer:** Never log tokens, passwords, full contracts, or personal data; redact fields and restrict access. Log a contract ID and correlation ID instead of the uploaded document content.

### 110. What is structured logging and why is it beneficial?
**Answer:** Structured logs store fields as JSON rather than unsearchable prose. A log with `contractId`, `userId`, `traceId`, `latencyMs`, and `outcome` is easy to query in production.

### 111. How do you implement correlation IDs for distributed tracing?
**Answer:** Accept or generate a request ID at the edge, place it in the logging context, propagate it in downstream headers, and return it to clients. One upload can then be followed across API and workers.

### 112. What is distributed tracing and which tools support it?
**Answer:** Tracing records spans across services to show latency and failures. OpenTelemetry, Jaeger, Zipkin, and Grafana Tempo can show an upload-to-embedding-to-chat trace.

### 113. How do you instrument a Java application for metrics collection?
**Answer:** Use Micrometer timers, counters, gauges, and distributions exported to Prometheus or another backend. Measure chat latency, cache hit ratio, upload failures, and active requests.

### 114. What are SLAs, SLOs, and error budgets and how do they relate?
**Answer:** An SLA is a customer promise, an SLO is an internal target, and the error budget is allowed unreliability. An SLO of 99.9% chat availability permits roughly 43 minutes of monthly unavailability.

### 115. How do you design health checks and readiness/liveness probes?
**Answer:** Liveness detects a stuck process; readiness says whether it can receive traffic. Readiness should check required dependencies carefully, while liveness should not restart a service merely because Ollama is temporarily unavailable.

### 116. What is blue-green deployment and how does it reduce risk?
**Answer:** Run old blue and new green environments, test green, then switch traffic and retain blue for rollback. It allows a quick return if a new embedding model breaks responses.

### 117. What is canary deployment and how do you implement it?
**Answer:** Send a small percentage of traffic to the new version, compare health and business metrics, then increase gradually. Route 5% of chat requests to a new prompt version before full rollout.

### 118. How do feature flags help in deployment strategies?
**Answer:** Flags decouple deployment from release and allow targeted rollout or quick disablement. Enable a new reranking algorithm only for internal users while measuring answer quality.

### 119. What is immutable infrastructure and why is it useful?
**Answer:** Replace servers or images instead of mutating live instances, making deployments reproducible. Build a new Docker image for each backend version and roll it through Kubernetes.

### 120. How do you containerize a Java application and what are best practices?
**Answer:** Use a multi-stage build, a small non-root runtime image, explicit ports, health checks, and JVM container-aware settings. Compile with JDK 21 and run the jar with a JRE image.

### 121. What is the difference between a Docker image and a container?
**Answer:** An image is an immutable package; a container is a running isolated instance of it. The backend image can produce many Kubernetes backend pods.

### 122. How do you optimize Docker images for Java apps?
**Answer:** Use multi-stage builds, layered jars, minimal base images, dependency caching, `.dockerignore`, and non-root users. Keep Maven and source code out of the production layer.

### 123. What is Kubernetes and what are its core primitives?
**Answer:** Kubernetes schedules and manages containers using Pods, Deployments, Services, Ingress, ConfigMaps, Secrets, StatefulSets, PVCs, and Jobs. The repository manifests use each according to workload needs.

### 124. How do you design services for Kubernetes (Deployments, Services, Ingress)?
**Answer:** Deploy stateless replicas with Deployments, expose stable DNS with Services, and route external HTTP with Ingress. Backend pods remain replaceable behind `backend-service`.

### 125. What is a sidecar pattern and when should you use it?
**Answer:** A sidecar is a helper container sharing a pod's lifecycle and network. A logging or proxy sidecar can handle collection or mTLS without changing application code.

### 126. How do you manage configuration in Kubernetes?
**Answer:** Put non-secret settings in ConfigMaps and credentials in Secrets, inject them as environment variables or mounted files, and version changes. Model names belong in ConfigMap; database passwords do not.

### 127. What are StatefulSets and when are they needed?
**Answer:** StatefulSets provide stable identity, ordered behavior, and persistent volume association. PostgreSQL needs them; stateless WebFlux API pods generally need Deployments.

### 128. How do you handle persistent storage for stateful Java services?
**Answer:** Use PersistentVolumeClaims backed by reliable storage, backups, restore tests, and appropriate performance classes. PostgreSQL data and Ollama models must survive pod replacement.

### 129. What is service mesh and what problems does it solve?
**Answer:** A mesh supplies service-to-service security, retries, traffic policy, and telemetry through proxies. It can provide mTLS and canary routing without embedding all networking logic in Java.

### 130. How do you perform rolling updates in Kubernetes?
**Answer:** Replace pods gradually while keeping ready replicas available, using readiness probes, resource limits, and rollout strategy settings. Verify the backend rollout before shifting all traffic.

### 131. What is autoscaling and how do you configure it for Java services?
**Answer:** Horizontal Pod Autoscaler adjusts replicas from CPU, memory, or custom metrics. Scale on request concurrency or latency for WebFlux rather than CPU alone when LLM calls dominate.

### 132. How do you monitor JVM metrics in production?
**Answer:** Export heap, non-heap, GC, threads, class loading, pool, and process metrics through Micrometer/JMX. Alert on sustained heap growth, long pauses, and exhausted executor queues.

### 133. What is a heap dump and how do you analyze it?
**Answer:** A heap dump captures live object graphs at a point in time. Use Eclipse MAT or VisualVM to find dominators, such as an unbounded response cache retaining millions of strings.

### 134. How do you analyze thread dumps to diagnose issues?
**Answer:** Take multiple dumps and compare blocked, waiting, runnable, and deadlocked threads. A WebFlux dump full of threads blocked in JDBC indicates blocking work on the wrong scheduler.

### 135. What is JFR (Java Flight Recorder) and how is it used?
**Answer:** JFR is low-overhead JVM event recording for CPU, allocation, locks, GC, and I/O. Record a production incident to find whether chat latency comes from GC, contention, or external calls.

### 136. How do you profile CPU and memory in a Java application?
**Answer:** Use JFR, async-profiler, VisualVM, or commercial profilers, preferably with representative load. Profile document parsing and embedding calls separately from request handling.

### 137. What are common causes of high GC pause times and how to mitigate them?
**Answer:** Causes include excessive allocation, oversized live sets, humongous objects, or poor heap sizing. Bound chat history, stream files, tune G1, and remove unnecessary large copies before changing flags.

### 138. How do you tune JVM flags for production workloads?
**Answer:** Start with container-aware defaults, measure with realistic load, then tune heap, GC, metaspace, and diagnostics. Avoid copying flags from another service without observing allocation and latency behavior.

### 139. What is classloader leak and how can it occur in application servers?
**Answer:** A long-lived parent retains classes or threads from an undeployed application classloader. Stop executors, deregister JDBC drivers, clear ThreadLocals, and close resources during shutdown.

### 140. How do you perform graceful shutdown of a Java service?
**Answer:** Stop accepting traffic, mark readiness false, finish or cancel in-flight work, close connections, and exit within a deadline. Kubernetes `preStop` and Spring graceful shutdown help protect active chat streams.

## Databases and Messaging

### 141. What is connection pooling and why is it important for databases?
**Answer:** A pool reuses established connections and limits database concurrency. HikariCP avoids paying TCP/authentication cost for every contract query, but an oversized pool can overload PostgreSQL.

### 142. How do you configure and tune a JDBC connection pool?
**Answer:** Set maximum size from database capacity and workload, plus timeout, idle, lifetime, and leak detection settings. Observe acquisition wait time rather than increasing the pool blindly.

### 143. What is prepared statement caching and why use it?
**Answer:** It reuses parsed query plans and reduces parsing overhead while preserving parameter binding. Repeated metadata lookups benefit from prepared statements without enabling SQL injection.

### 144. How do you handle database migrations in production?
**Answer:** Use versioned, repeatable migrations, backups, compatibility checks, and expand-migrate-contract sequencing. Add a nullable column first, deploy code that writes both, migrate data, then remove the old column later.

### 145. What are Flyway and Liquibase and how do they differ?
**Answer:** Both manage schema migrations; Flyway emphasizes ordered SQL/scripts, while Liquibase models changesets and supports more database abstraction. Choose one consistently and run it in deployment automation.

### 146. How do you design a schema for high write throughput?
**Answer:** Keep rows focused, choose efficient indexes, batch writes, partition where useful, and avoid unnecessary synchronous work. Batch document chunks rather than inserting each token individually.

### 147. What is database sharding and how do you implement it?
**Answer:** Sharding distributes rows across database instances using a shard key such as tenant ID. Route each tenant consistently and accept the complexity of cross-shard queries and migrations.

### 148. What is replication and how does it improve availability?
**Answer:** Replicas copy data from a primary for read scale and failover. Route contract searches to replicas only if the application can tolerate replication lag after upload.

### 149. How do you handle failover and leader election for databases?
**Answer:** Use a managed database or proven operator, health checks, a consensus-backed leader, and clients that reconnect safely. Never let two database primaries accept conflicting writes.

### 150. What is eventual consistency in NoSQL databases and examples?
**Answer:** Writes may appear at different replicas at different times. A distributed chat history may briefly differ between regions, so clients can display a synchronization state and reconcile by message ID.

### 151. When should you choose a relational database vs a NoSQL database?
**Answer:** Choose relational databases for transactions, joins, constraints, and structured relationships; NoSQL for flexible massive-scale access patterns. PostgreSQL fits contract metadata and pgvector because ownership and indexing matter.

### 152. What are document stores, key-value stores, columnar stores, and graph databases?
**Answer:** Document stores hold JSON-like records, key-value stores optimize lookup by key, columnar stores suit analytics, and graph databases model relationships. Redis is key-value; PostgreSQL is relational with JSONB support.

### 153. How do you model relationships in a document database?
**Answer:** Embed tightly coupled small data or reference independently changing/large data. Embed a small chat summary but reference large contract chunks by ID.

### 154. What is indexing and how does it affect query performance?
**Answer:** An index accelerates lookup by maintaining searchable structures but costs write time and storage. Index `user_id` and vector metadata because every query filters by ownership.

### 155. How do composite and covering indexes work?
**Answer:** Composite indexes order multiple columns for combined predicates; covering indexes contain all fields needed by a query. `(user_id, uploaded_at)` supports a user's recent contracts efficiently.

### 156. What is query plan and how do you analyze it?
**Answer:** A query plan shows scans, joins, estimated costs, and row counts. Use `EXPLAIN ANALYZE` to verify PostgreSQL uses the intended metadata or vector index.

### 157. How do you avoid full table scans and expensive joins?
**Answer:** Filter selectively, index predicates, return only needed columns, paginate, and inspect plans. Do not load every document chunk into Java to find one contract.

### 158. What is denormalization and when is it appropriate?
**Answer:** Denormalization duplicates data to optimize reads at the cost of update complexity. Store a searchable filename alongside vector metadata when it avoids a hot join and can be updated reliably.

### 159. How do you handle time series data in backend systems?
**Answer:** Store timestamped events with retention, partitioning, and time-based indexes, then aggregate for dashboards. Record request latency samples separately from transactional contract data.

### 160. What is CQRS and when should you use it?
**Answer:** Command Query Responsibility Segregation separates write and read models. Use it when search/read projections differ greatly from transactional writes; do not add it to a simple CRUD endpoint without need.

### 161. What is event sourcing and what are its benefits and drawbacks?
**Answer:** Event sourcing stores immutable events as the source of truth and rebuilds state by replay. It offers auditability but adds schema evolution, replay, and operational complexity.

### 162. How do you design an event schema and handle versioning?
**Answer:** Include event type, ID, timestamp, producer, correlation ID, version, and compatible payload fields. Add optional fields rather than changing the meaning of existing fields.

### 163. What is a message broker and examples (Kafka, RabbitMQ)?
**Answer:** A broker buffers and routes messages between producers and consumers. RabbitMQ suits work queues and routing; Kafka suits durable ordered event streams and replay.

### 164. How do Kafka partitions and consumer groups work?
**Answer:** Partitions provide ordered logs and parallelism; one consumer in a group owns each partition at a time. Partition by contract ID to preserve event order for one contract.

### 165. What is at-least-once, at-most-once, and exactly-once delivery semantics?
**Answer:** At-least-once may duplicate, at-most-once may lose, and exactly-once aims to avoid both within defined boundaries. Most services use at-least-once plus idempotent consumers.

### 166. How do you implement idempotent consumers for message processing?
**Answer:** Store a unique event ID or use a natural idempotency key in the same transaction as the effect. Reprocessing an embedding job should detect that its chunk version already exists.

### 167. What is a dead-letter queue and how do you use it?
**Answer:** A DLQ stores messages that repeatedly fail so the main flow continues. Put malformed PDFs there with error metadata, alert operators, and provide a replay tool after correction.

### 168. How do you handle message ordering guarantees?
**Answer:** Partition by the entity requiring order, process sequentially per partition, and include sequence numbers. Contract update events should not be applied before the contract creation event.

### 169. What is Kafka Streams and how does it differ from consumer APIs?
**Answer:** The consumer API gives low-level record control; Kafka Streams adds stateful transformations, joins, windows, and materialized stores. Use it for streaming analytics such as per-tenant usage totals.

### 170. How do you implement transactional messaging with Kafka?
**Answer:** Kafka transactions atomically publish records and commit offsets for Kafka workflows; the outbox pattern covers database plus broker boundaries. Write a contract event to an outbox in the same PostgreSQL transaction.

### 171. What is schema registry and why use Avro/Protobuf for messages?
**Answer:** A registry stores and validates message schemas and compatibility. Avro or Protobuf gives compact typed contracts, preventing consumers from silently breaking after a producer change.

### 172. How do you design retry and backoff strategies for message processing?
**Answer:** Classify transient versus permanent errors, retry with bounded exponential backoff and jitter, then send failures to a DLQ. Do not retry a permanently corrupt PDF forever.

## Microservices and Distributed Systems

### 173. What is the role of a gateway or API gateway in microservices?
**Answer:** It centralizes routing, TLS, authentication, rate limiting, and sometimes aggregation. Route `/api/*` to backend pods while serving the frontend from the same public host.

### 174. How do you implement authentication and rate limiting at the gateway?
**Answer:** Validate tokens at the edge, attach verified identity, and apply per-IP, client, or user quotas with Redis or gateway-native policy. Services still enforce resource authorization.

### 175. What is service discovery and how is it implemented?
**Answer:** Services find instances dynamically through DNS, a registry, or a platform control plane. Kubernetes Service DNS lets the backend call `postgres-service` without knowing pod IPs.

### 176. How do you handle inter-service communication (REST, gRPC, messaging)?
**Answer:** REST is interoperable, gRPC is efficient and typed, and messaging decouples timing. Use REST for browser APIs, gRPC for internal low-latency calls, and events for asynchronous indexing.

### 177. What is the strangler pattern for migrating monoliths to microservices?
**Answer:** Gradually route one capability at a time to a new service while the old system remains live. Extract document ingestion first, then move chat after observing behavior.

### 178. How do you manage data ownership and boundaries in microservices?
**Answer:** Each service owns its data and exposes behavior rather than shared tables. An ingestion service owns processing state; chat queries a stable contract-search API.

### 179. What is the anti-corruption layer pattern?
**Answer:** An adapter translates an external model into the internal domain model, preventing legacy concepts from spreading. Convert a legacy contract status code into a clean internal enum.

### 180. How do you perform contract testing between services?
**Answer:** Verify provider behavior against consumer expectations using OpenAPI, Pact, or generated stubs. Test that the chat service still accepts the ingestion service's contract metadata response.

### 181. What is consumer-driven contract testing and tools for it?
**Answer:** Consumers publish expectations and providers verify them in CI. Pact can ensure a frontend or chat client receives required fields before deployment.

### 182. How do you handle cross-service transactions and consistency?
**Answer:** Use local transactions, events, outbox, idempotency, retries, and compensation. Mark a contract `INDEXING` locally and transition to `READY` only after an indexing completion event.

### 183. What is eventual consistency compensation and compensating transactions?
**Answer:** Compensation performs a corrective action when a later step fails. If a notification fails after indexing, retry notification or record it for repair rather than undoing valid indexing.

### 184. How do you implement distributed tracing across microservices?
**Answer:** Instrument HTTP, messaging, database, and executor boundaries with OpenTelemetry context propagation. One trace should connect upload, chunking, embedding, database insertion, and chat retrieval.

### 185. What are common causes of cascading failures and how to prevent them?
**Answer:** Unbounded retries, shared pools, slow dependencies, and missing timeouts cause cascades. Use deadlines, circuit breakers, bulkheads, bounded queues, and graceful degradation.

### 186. How do you design for observability in microservices?
**Answer:** Emit correlated logs, metrics, traces, health signals, and useful business events. Track “documents indexed” and “answers returned” alongside CPU and latency.

### 187. What is the difference between logging, metrics, and tracing?
**Answer:** Logs explain individual events, metrics aggregate trends, and traces explain one request across components. Use metrics to detect rising latency, traces to locate Ollama, and logs for the exact error.

### 188. How do you implement structured logs and correlate them with traces?
**Answer:** Emit JSON fields including trace/span IDs, service, operation, tenant, and outcome, with sensitive values redacted. Log the same trace ID in API and worker records.

### 189. What is OpenTelemetry and how do you instrument Java apps with it?
**Answer:** OpenTelemetry is a vendor-neutral framework for traces, metrics, and logs. Add Java agents or SDK instrumentation, export OTLP data, and propagate context through WebFlux and messaging.

### 190. How do you set up alerting and on-call practices for backend services?
**Answer:** Alert on user-impacting symptoms and actionable causes, route ownership clearly, and link runbooks. Alert on sustained chat error rate or queue age, not every single transient timeout.

### 191. What is a runbook and what should it contain?
**Answer:** A runbook gives symptoms, checks, safe commands, escalation, rollback, and recovery steps. The analyzer runbook should cover Redis failure, Ollama health, database capacity, and Kubernetes rollback.

### 192. How do you perform capacity planning for backend services?
**Answer:** Measure workload, concurrency, resource cost, growth, and failure headroom using load tests and production telemetry. Estimate simultaneous chat streams and embedding throughput before choosing pod counts.

### 193. What is horizontal vs vertical scaling and tradeoffs?
**Answer:** Horizontal adds instances for resilience and throughput; vertical adds resources to one instance but has limits and larger failure impact. Scale WebFlux replicas horizontally, but size Ollama nodes for model memory.

### 194. How do you design for multi-region deployments and data locality?
**Answer:** Place compute near users/data, replicate appropriately, route traffic intelligently, and define consistency and failover behavior. Keep regulated contract data in its allowed region.

### 195. What is eventual consistency across regions and how to handle it?
**Answer:** Replicas may lag across regions; expose freshness/version metadata, use conflict resolution, and route sensitive reads to the authoritative region. A freshly uploaded contract may temporarily be searchable only locally.

### 196. How do you secure inter-service communication in a multi-tenant environment?
**Answer:** Use mTLS, workload identity, least-privilege authorization, tenant context validation, and encryption at rest. Never rely only on a tenant header supplied by a caller.

### 197. What is tenant isolation and strategies to implement it?
**Answer:** Isolation prevents one tenant seeing or affecting another; use separate databases, schemas, row-level security, or strict tenant-scoped keys. Include tenant ID in vector metadata, SQL predicates, and Redis keys.

### 198. How do you design APIs for backward compatibility?
**Answer:** Add optional fields, preserve meanings, tolerate unknown fields, avoid breaking status changes, and deprecate gradually. Add `displayName` without removing `filename` from contract responses.

### 199. What is semantic versioning and how does it apply to APIs and services?
**Answer:** Major means breaking change, minor adds compatible functionality, and patch fixes bugs. A removed API field requires a major version or a migration period.

### 200. How do you perform blue/green database migrations with minimal downtime?
**Answer:** Use expand-migrate-contract: add compatible schema, deploy dual-read/write code, migrate data, switch reads, then remove old structures. Never deploy code requiring a column before every instance can tolerate it.

## Delivery, Testing, and Quality

### 201. What is feature toggling and how do you manage feature flag lifecycle?
**Answer:** Flags control behavior independently of deployment; assign owners, expiry dates, defaults, and audit changes. Remove a temporary “new-reranker” flag after rollout rather than leaving permanent branches.

### 202. How do you implement A/B testing in backend services?
**Answer:** Assign stable users to variants, isolate exposure, record outcomes, and compare statistically meaningful metrics. Compare two prompt templates using the same contract and user cohorts.

### 203. What is canary analysis and automated rollback criteria?
**Answer:** Compare a small release against baseline using error rate, latency, resource use, and business quality. Roll back if p99 latency or grounded-answer failure exceeds a defined threshold.

### 204. How do you manage secrets and credentials in CI/CD pipelines?
**Answer:** Use the CI secret store or workload identity, mask logs, rotate credentials, and avoid long-lived keys. The pipeline should obtain a short-lived registry token rather than echoing a password.

### 205. What are best practices for dependency management in Java (Maven/Gradle)?
**Answer:** Pin versions through a BOM, use lock/dependency reports, update regularly, scan vulnerabilities, and keep dependencies minimal. Spring Boot's dependency management keeps compatible library versions aligned.

### 206. How do you handle transitive dependency conflicts and dependency hell?
**Answer:** Inspect the dependency tree, identify the selected version, use dependency management or exclusions carefully, and run tests. Do not exclude a library merely because the tree looks large.

### 207. What is a fat jar vs thin jar and when to use each?
**Answer:** A fat jar bundles application dependencies for simple deployment; a thin jar expects dependencies externally and can improve layer reuse. Spring Boot's executable jar is convenient for Docker.

### 208. How do you create reproducible builds for Java applications?
**Answer:** Pin JDK, Maven/plugin/dependency versions, use a lock or verified repository, set stable timestamps where supported, and build in a controlled container. The same commit should produce equivalent artifacts.

### 209. What is semantic release and automated versioning?
**Answer:** Semantic release derives versions and changelogs from conventional commits and verified tests. It can publish a backend artifact only after the CI pipeline passes.

### 210. How do you run integration tests that require external services?
**Answer:** Start real dependencies in isolated environments, configure dynamic endpoints, wait for readiness, and clean up. Test the analyzer against PostgreSQL and Redis rather than mocking every driver behavior.

### 211. What are Testcontainers and how do they help integration testing?
**Answer:** Testcontainers starts disposable Docker containers from tests. A PostgreSQL container with pgvector and a Redis container can verify migrations, vector storage, and caching consistently in CI.

### 212. How do you write effective unit tests for Spring components?
**Answer:** Test one behavior at a time, mock true boundaries, assert outcomes and important interactions, and cover errors. Unit-test rate-limit decisions separately from Redis integration tests.

### 213. What is mocking and when should you use real vs mocked dependencies?
**Answer:** Mocks isolate a unit and control failures; real dependencies reveal integration errors. Mock Ollama in service unit tests, but use a real PostgreSQL/Redis container for repository behavior.

### 214. How do you test asynchronous code and concurrency scenarios?
**Answer:** Use deterministic schedulers, timeouts, latches, stress tests, and assertions on eventual outcomes. Test that simultaneous identical uploads do not create duplicate work.

### 215. What is contract testing and why is it important for microservices?
**Answer:** It verifies an interface independently of full end-to-end environments, catching incompatible changes early. Validate the chat API's request and streaming response contract in CI.

### 216. How do you measure code coverage and what are its limitations?
**Answer:** Tools such as JaCoCo measure executed lines, branches, or methods, but coverage does not prove assertions are meaningful. A test can execute a catch block without checking the returned error.

### 217. What is mutation testing and how does it improve test quality?
**Answer:** Mutation tools alter operators or conditions and check whether tests fail. If changing a rate-limit boundary from `<=20` to `<20` passes, the tests are weak.

### 218. How do you implement CI pipelines for Java projects (Jenkins/GitHub Actions)?
**Answer:** Checkout, verify formatting, compile, unit-test, integration-test, scan, build the image, publish an immutable artifact, and deploy with approval. Cache Maven dependencies but never skip security checks.

### 219. What is artifact promotion and how do you manage release artifacts?
**Answer:** Build once, store the immutable artifact, and promote the same digest through environments. Do not rebuild different binaries for staging and production.

### 220. How do you roll back a bad deployment safely?
**Answer:** Keep a known-good image and schema-compatible code, stop rollout, restore traffic, inspect impact, and preserve evidence. Kubernetes `rollout undo` is useful only when database changes remain backward compatible.

### 221. What is chaos engineering and how can it improve system resilience?
**Answer:** Controlled experiments inject realistic failures to test hypotheses and recovery. Stop Redis briefly and verify the service degrades without losing PostgreSQL contract data.

### 222. How do you design experiments for chaos testing?
**Answer:** Define a steady-state metric, one failure hypothesis, limited blast radius, abort conditions, and recovery verification. Test one backend pod failure while maintaining the availability SLO.

### 223. What is technical debt and how do you prioritize paying it down?
**Answer:** Debt is future cost from shortcuts, measured by risk, interest, and impact. Prioritize insecure secrets, data-loss paths, and operational bottlenecks before cosmetic refactoring.

### 224. How do you perform a postmortem and what should it include?
**Answer:** Write a blameless timeline, impact, detection, root/contributing causes, what worked, actions with owners/dates, and verification. A Redis outage postmortem should produce tested fallback and alert improvements.

### 225. What are common performance bottlenecks in Java backend apps?
**Answer:** Slow queries, blocking I/O, excessive allocations, lock contention, network calls, poor pools, and inefficient serialization are common. Measure whether the analyzer is waiting on pgvector or Ollama before tuning Java.

### 226. How do you benchmark and load test a Java service?
**Answer:** Define realistic scenarios, warm up the JVM, control data and clients, measure throughput/latency/error percentiles, and identify saturation. Load test concurrent chat streams and large PDF uploads separately.

### 227. What tools are used for load testing (JMeter, Gatling, k6)?
**Answer:** JMeter is GUI/script friendly, Gatling offers code-based scenarios, and k6 is developer-friendly JavaScript. Use one to model upload, cache-hit chat, and cache-miss chat traffic.

### 228. How do you interpret p95, p99 latency metrics and why do they matter?
**Answer:** p95 is the latency below which 95% of requests finish; p99 exposes the slowest 1%. A good average can hide users waiting 30 seconds for overloaded Ollama.

### 229. What is tail latency and how do you reduce it?
**Answer:** Tail latency is the slow end of the distribution, often caused by queues, retries, GC, or stragglers. Bound queues, set deadlines, isolate workloads, and avoid retry storms.

### 230. How do you design for graceful degradation under load?
**Answer:** Preserve core operations while reducing optional work: serve cached answers, limit uploads, queue indexing, or return a processing status. Never take down chat because analytics aggregation is slow.

## HTTP, Files, Time, and Serialization

### 231. What is connection multiplexing (HTTP/2) and benefits for backend services?
**Answer:** HTTP/2 carries multiple streams over one connection, reducing handshakes and head-of-line issues at the application layer. It can improve many concurrent API calls, but server limits still matter.

### 232. How do you implement streaming APIs and handle backpressure?
**Answer:** Return a reactive `Flux`, encode events as SSE or chunked data, flush progressively, and bound buffering. Stream each LLM token while handling disconnected clients and cancellation.

### 233. What is WebSocket and when to use it over HTTP polling?
**Answer:** WebSocket provides bidirectional persistent communication; polling repeatedly asks for changes. Use WebSocket for interactive collaboration, but SSE is often simpler for one-way streamed AI answers.

### 234. How do you handle large file uploads and streaming in Java?
**Answer:** Stream to bounded temporary or object storage, enforce size/type limits, scan content, and avoid loading the whole file into heap. Process a 200 MB PDF in chunks rather than one byte array.

### 235. What is multipart/form-data and how to process it securely?
**Answer:** It encodes fields and files in one HTTP request. Validate declared and detected type, size, filename, path traversal, decompression bombs, and virus content before processing.

### 236. How do you implement resumable uploads or chunked uploads?
**Answer:** Assign an upload ID, accept numbered chunks with checksums, store progress durably, and assemble only after all chunks arrive. Retry chunk 7 without re-uploading a 2 GB document.

### 237. What is content negotiation and how is it implemented in REST?
**Answer:** Client and server select representation through headers such as `Accept` and `Content-Type`. Return JSON for normal APIs and `text/event-stream` for streamed chat.

### 238. How do you design APIs for internationalization and time zones?
**Answer:** Store instants in UTC, accept explicit offsets or zones, localize at presentation, and use message keys rather than hardcoded text. Contract deadlines should not shift when a user changes time zone.

### 239. What are best practices for date/time handling in Java (java.time)?
**Answer:** Use `Instant` for machine timestamps, `LocalDate` for dates without time, and `ZonedDateTime` when a zone matters; avoid legacy `Date` where possible. Store `uploaded_at` as an unambiguous timestamp.

### 240. How do you handle localization and message bundles in backend services?
**Answer:** Store translated templates in message bundles, select locale from validated preferences, and keep business data language-neutral. Return localized validation errors while storing canonical contract content.

### 241. What is protobuf and how does it compare to JSON for APIs?
**Answer:** Protobuf is compact, typed, and generated; JSON is human-readable and broadly interoperable. Use JSON for browser-facing APIs and Protobuf for high-volume internal messaging.

### 242. How do you design backward-compatible protobuf schemas?
**Answer:** Never reuse field numbers, reserve removed fields, add optional fields, and maintain compatible types. Add `processing_status` with a new field number instead of changing an existing field's meaning.

### 243. What is gRPC streaming and use cases for it?
**Answer:** gRPC supports server, client, or bidirectional streams over HTTP/2. Stream embedding batches or document-processing progress between trusted internal services.

### 244. How do you secure gRPC services (TLS, authentication)?
**Answer:** Use TLS or mTLS, validate identity and authorization metadata, set deadlines, and limit message sizes. Do not expose an unauthenticated embedding endpoint to the public network.

### 245. What is Thrift and when might you use it?
**Answer:** Thrift is an IDL and RPC framework that generates clients for multiple languages. Use it only when an existing organization-wide Thrift ecosystem justifies it over gRPC or REST.

### 246. How do you implement multipart responses or server-sent events?
**Answer:** Multipart responses carry separate parts; SSE sends named text events over a long-lived HTTP response. Set the correct content type, flush events, handle cancellation, and send an explicit completion/error event.

### 247. What is the role of a reverse proxy and examples (Nginx, Envoy)?
**Answer:** A reverse proxy terminates TLS, routes requests, applies limits, and hides internal services. Nginx can serve React on port 80 and route `/api` to Spring Boot on 8080.

### 248. How do you configure TLS termination and certificate management?
**Answer:** Terminate TLS at a gateway or ingress, automate certificate issuance/renewal, enforce modern protocols, and forward identity safely. Kubernetes Ingress can use a managed certificate for the public analyzer URL.

### 249. What is OCSP stapling and why is it useful?
**Answer:** OCSP stapling lets the server attach a certificate-status proof, reducing client revocation-check latency and privacy leakage. Enable it at the TLS proxy where supported.

### 250. How do you implement mutual TLS in a microservices environment?
**Answer:** Issue workload certificates from a trusted CA, validate SAN identities, rotate automatically, and authorize service identities. A mesh can manage this more reliably than hand-built certificate code.

### 251. What is consistent hashing and where is it used?
**Answer:** Consistent hashing minimizes key movement when nodes change. Use it to route tenant cache keys to cache nodes while preserving most locality during scaling.

### 252. How do you implement leader election in distributed systems?
**Answer:** Use a consensus-backed lease with unique identity, renewal, expiry, and fencing; do not rely on a local boolean. A single leader can run scheduled reindex coordination.

### 253. What are consensus algorithms (Paxos, Raft) and when to use them?
**Answer:** They let nodes agree on ordered state despite failures. Use a proven database/coordination system implementing Raft rather than writing consensus for application scheduling.

### 254. How do you design a distributed queue with ordering guarantees?
**Answer:** Partition by ordering key, persist messages, track acknowledgments, retry safely, and scale consumers per partition. Keep all events for one contract in one ordered partition.

### 255. What is the difference between push and pull models in messaging?
**Answer:** Push brokers deliver messages to consumers; pull consumers request batches at their pace. Pull gives Kafka consumers natural backpressure and replay control.

### 256. How do you handle schema evolution for persisted events and messages?
**Answer:** Version schemas, support old readers, migrate deliberately, and retain compatibility tests. A new chunk event field should be optional so old workers continue processing it.

### 257. What is data partitioning and how does it affect queries and joins?
**Answer:** Partitioning divides data by key or range for scale and maintenance but can make cross-partition joins expensive. Partition vector metadata by tenant only when query volume justifies the complexity.

### 258. How do you design a search index for fast text search (Elasticsearch)?
**Answer:** Define mappings, analyzers, shard/replica counts, filters, relevance tests, and refresh policy. Index contract text with tenant and contract filters, never relying on text relevance for authorization.

### 259. What are analyzers, tokenizers, and mappings in Elasticsearch?
**Answer:** Analyzers normalize text, tokenizers split it, and mappings define field types and search behavior. A lowercase English analyzer helps match “Termination” and “termination”.

### 260. How do you handle reindexing and zero-downtime index migrations?
**Answer:** Build a new versioned index, backfill it, verify counts and queries, then atomically switch an alias. Keep the old index until rollback is no longer needed.

### 261. What is eventual consistency in search indexes and how to mitigate it?
**Answer:** A committed database write may not be searchable immediately. Show indexing status, use refresh controls selectively, and let users retry rather than claiming the document was lost.

### 262. How do you implement full-text search vs database search tradeoffs?
**Answer:** PostgreSQL search is simpler and transactional; Elasticsearch offers richer relevance, analyzers, and scale at operational cost. Start with PostgreSQL unless search requirements exceed it.

### 263. What is rate limiting at the API level vs per user vs per IP?
**Answer:** Global limits protect the service, user limits enforce fairness, and IP limits protect unauthenticated endpoints. Combine them because many users may share one corporate IP.

### 264. How do you implement throttling and fair usage policies?
**Answer:** Allocate quotas by plan, tenant, or user, use token buckets, expose remaining quota, and queue expensive work. Premium tenants may receive more embedding throughput without starving others.

### 265. What is the role of a message broker in event-driven architectures?
**Answer:** It decouples producers and consumers, buffers bursts, supports retries, and can replay durable events. Upload completion can notify indexing, audit, and analytics independently.

### 266. How do you design event schemas to be extensible and versionable?
**Answer:** Use stable IDs, timestamps, producer, correlation, version, optional fields, and explicit semantics. Consumers should ignore unknown fields and validate required invariants.

### 267. What is the difference between synchronous and asynchronous communication?
**Answer:** Synchronous calls wait for an immediate response; asynchronous communication sends work and continues. HTTP chat retrieval may be synchronous, while PDF embedding is asynchronous for resilience.

### 268. How do you choose between push notifications and polling for updates?
**Answer:** Push reduces latency and wasted requests but needs connection management; polling is simpler and often adequate at low frequency. Use SSE for indexing progress if users need immediate updates.

### 269. What is the role of a scheduler (cron) vs event triggers in backend systems?
**Answer:** Schedulers run time-based work; events react to facts immediately. Use a nightly cleanup scheduler and an upload event to start indexing now.

### 270. How do you implement delayed or scheduled message processing?
**Answer:** Store due time, use broker delay features or a scheduler, claim work transactionally, and make execution idempotent. Retry an embedding job in five minutes after a temporary Ollama outage.

### 271. What is a poison message and how should systems handle it?
**Answer:** It always fails due to invalid data or a deterministic bug. Limit retries, record diagnostics, send it to a DLQ, and alert without blocking healthy messages.

### 272. How do you ensure data integrity during partial failures?
**Answer:** Use atomic local transactions, durable state transitions, checksums, idempotency, reconciliation, and compensating actions. Mark a document ready only after all expected chunks are verified.

### 273. What is the role of checksums and idempotency in distributed data pipelines?
**Answer:** Checksums detect corruption or changed content; idempotency prevents duplicate effects. Hash each upload and skip re-indexing if the same tenant already processed that content version.

### 274. How do you design a bulk import/export API for large datasets?
**Answer:** Make it asynchronous, stream data, validate limits, report job status, provide signed downloads, and support cancellation. Return a job ID rather than holding an HTTP request for hours.

### 275. What are best practices for API documentation (OpenAPI/Swagger)?
**Answer:** Document schemas, examples, errors, authentication, pagination, limits, and streaming behavior; generate validation or clients where useful. Show a real `/api/chat/stream` request and response event.

### 276. How do you generate client SDKs from API specifications?
**Answer:** Maintain an accurate OpenAPI or Protobuf contract, generate in CI, publish versioned clients, and test generated code against the provider. Do not hand-edit generated files.

### 277. What is CORS preflight and how does it affect API design?
**Answer:** Browsers send an OPTIONS request before certain cross-origin calls to check permission. Cache safe preflight responses and allow exactly the headers/methods the frontend needs.

### 278. How do you implement multipart caching and cache control headers?
**Answer:** Set `Cache-Control`, validators, and varying headers deliberately for each representation; private authenticated data should not be publicly cached. Static frontend assets can be immutable while chat responses remain private.

### 279. What is the difference between strong and weak ETags?
**Answer:** Strong ETags represent byte-identical content; weak ETags indicate semantic equivalence. Use a strong hash for a downloadable contract file and weak validation for reformatted JSON if appropriate.

### 280. How do you implement optimistic concurrency control in REST APIs?
**Answer:** Return an ETag/version and require `If-Match` on updates; reject stale writes with 412. Two users editing the same contract metadata cannot silently overwrite one another.

### 281. What is the role of a CDN for dynamic vs static content?
**Answer:** CDNs excel at cacheable static content and carefully cacheable public responses; personalized dynamic content needs privacy and invalidation controls. Cache React assets aggressively, not tenant-specific answers.

### 282. How do you design a multi-tenant database schema (shared vs isolated)?
**Answer:** Shared tables are economical but require strict tenant predicates/RLS; separate schemas or databases improve isolation at operational cost. Include tenant ID in every contract and vector record.

### 283. What are the tradeoffs of single database per tenant vs shared schema?
**Answer:** Per-tenant databases improve isolation and independent backup but multiply operations; shared schema simplifies operations but increases authorization risk. Choose based on compliance, scale, and tenant size.

### 284. How do you handle GDPR and data deletion requests in backend systems?
**Answer:** Identify all copies, delete or anonymize source files, vectors, caches, logs, backups according to policy, and record completion. Cache invalidation must be part of contract deletion, not an afterthought.

### 285. What is data masking and tokenization for sensitive data?
**Answer:** Masking hides values for display; tokenization replaces sensitive values with reversible or non-sensitive tokens stored separately. Redact payment details before sending contract text to logs or analytics.

### 286. How do you implement audit logging and immutable event logs?
**Answer:** Record actor, action, resource, timestamp, outcome, and correlation ID in append-only storage with restricted access. Audit who downloaded or deleted a contract without logging its contents.

### 287. What is the principle of least privilege and how to apply it to services?
**Answer:** Grant only required permissions for the shortest necessary scope. The chat service needs read access to contract vectors, not schema-altering database rights.

### 288. How do you perform dependency vulnerability scanning and remediation?
**Answer:** Scan direct and transitive dependencies and container images in CI, prioritize exploitable runtime issues, update/test, and document accepted risk. Review Maven dependency reports on every release.

### 289. What is supply chain security for Java applications and best practices?
**Answer:** Verify sources, pin and scan dependencies, protect CI, sign artifacts, use SBOMs, and restrict build permissions. A compromised Maven dependency should be detectable before reaching production.

### 290. How do you migrate a monolith to microservices with minimal risk?
**Answer:** Establish observability and contracts, identify a bounded capability, use a strangler route, migrate data carefully, and measure before continuing. Extract ingestion while leaving chat stable.

### 291. What is domain-driven design and how does it influence service boundaries?
**Answer:** DDD models business language, bounded contexts, aggregates, and domain events. “Contract,” “document processing,” and “conversation” may become separate contexts with explicit interfaces.

### 292. How do you identify bounded contexts and aggregates in DDD?
**Answer:** Look for distinct language, invariants, ownership, and transaction boundaries. A contract aggregate may protect status transitions, while individual vector chunks need separate storage behavior.

### 293. What is the repository pattern and how does it relate to JPA?
**Answer:** A repository abstracts persistence operations from domain/application logic. Spring Data repositories provide this boundary, but avoid hiding complex queries behind misleading generic methods.

### 294. How do you implement CQRS with event sourcing in Java?
**Answer:** Commands validate and append events; projections consume events into read models, and queries read projections. Use it for auditable contract workflow history only when the operational complexity is justified.

### 295. What are common anti-patterns in microservices and how to avoid them?
**Answer:** Avoid shared databases, distributed monoliths, chatty calls, synchronous chains, duplicate logic, and independent deployment without contracts. Keep boundaries meaningful and use events where coupling would otherwise grow.

### 296. How do you design a high-throughput, low-latency messaging pipeline?
**Answer:** Partition by key, batch safely, reuse connections, minimize serialization, bound queues, use async I/O, and monitor lag. Batch embeddings while preserving per-contract ordering and tenant isolation.

### 297. What are practical strategies for debugging production issues in Java services?
**Answer:** Start with impact and timeline, inspect dashboards/logs/traces, compare recent changes, capture thread/JFR data safely, reproduce with sanitized inputs, and make the smallest reversible fix.

### 298. How do you perform root cause analysis after an incident?
**Answer:** Separate trigger, contributing conditions, and missing defenses; validate with evidence and create owned preventive actions. “Redis restarted” is a trigger, not the root cause if no persistence or alert existed.

### 299. What are best practices for code reviews and maintaining code quality?
**Answer:** Review correctness, security, failure modes, observability, tests, API compatibility, and maintainability; keep changes focused. For a cache change, ask about stale data, tenant isolation, TTL, and failure behavior.

### 300. How do you keep up with evolving Java ecosystem features and choose the right tools?
**Answer:** Follow JDK/Spring release notes, official documentation, security advisories, benchmarks, and project needs; adopt incrementally. Upgrade Java or Spring after compatibility tests prove the contract analyzer's behavior remains correct.

## Final Interview Tip

For any answer, connect the concept to a tradeoff, failure mode, and measurable example: latency, throughput, consistency, security, cost, or operability. That turns memorized definitions into credible engineering explanations.
