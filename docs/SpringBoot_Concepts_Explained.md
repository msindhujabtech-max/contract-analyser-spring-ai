# Spring Boot Concepts — Explained Simply
## Every concept: The Problem → The Fix → Example → Analogy → Interview Line

Read top to bottom. Each concept builds understanding, not just memorization.

---

# PART 1: THE CORE — IoC & Dependency Injection

---

## 1. Inversion of Control (IoC)

**The problem**: Normally YOUR code creates objects:
```java
public class OrderService {
    private PaymentService payment = new PaymentService();  // I create it myself
}
```
This is tight coupling — `OrderService` is stuck with one specific `PaymentService`. Hard to test, hard to swap.

**The fix (IoC)**: Let a container (Spring) create and manage objects. You just declare what you need; Spring hands it to you. Control is "inverted" — the framework controls object creation, not you.

**Example**:
```java
@Service
public class OrderService {
    private final PaymentService payment;
    public OrderService(PaymentService payment) {  // Spring gives it to me
        this.payment = payment;
    }
}
```

**Analogy**: Instead of cooking your own food (creating objects), you go to a restaurant (Spring container) — you order, the kitchen prepares and serves. You don't manage the kitchen.

**Interview line**: "IoC means the Spring container controls object creation and wiring instead of my code. I declare dependencies; Spring provides them. This gives loose coupling and testability."

---

## 2. Dependency Injection (DI)

**The problem**: Objects need other objects (dependencies). Creating them manually causes tight coupling.

**The fix**: DI is HOW IoC is achieved — Spring "injects" the dependencies into your class. Three ways:

**Example (all three)**:
```java
// 1. Constructor injection (BEST)
@Service
public class OrderService {
    private final PaymentService payment;
    public OrderService(PaymentService payment) { this.payment = payment; }
}

// 2. Setter injection
@Service
public class OrderService {
    private PaymentService payment;
    @Autowired
    public void setPayment(PaymentService p) { this.payment = p; }
}

// 3. Field injection (avoid)
@Service
public class OrderService {
    @Autowired private PaymentService payment;
}
```

**Analogy**: You don't build your car's engine — the factory *injects* a ready engine into the car. You just use it.

**Interview line**: "DI is how Spring implements IoC — it injects dependencies via constructor, setter, or field. I prefer constructor injection because it allows `final` fields and makes dependencies explicit."

---

## 3. The IoC Container / ApplicationContext

**The problem**: Who actually creates and stores all these beans?

**The fix**: The **ApplicationContext** (the IoC container). At startup it scans, creates all beans, wires dependencies, and keeps them in memory for the app's lifetime.

**Analogy**: A warehouse manager who stocks every tool (bean) and hands the right one to whoever asks.

**Interview line**: "The ApplicationContext is Spring's IoC container. It instantiates beans, resolves dependencies, and manages their lifecycle."

---

# PART 2: BEAN ANNOTATIONS

---

## 4. @Component

**The problem**: How does Spring know which classes to manage as beans?

**The fix**: Mark a class with `@Component`. Spring's component scanning finds it and creates a bean.

**Example**:
```java
@Component
public class EmailValidator {
    public boolean isValid(String email) { return email.contains("@"); }
}
```

**Analogy**: Putting a "MANAGE ME" sticker on a class so the warehouse manager stocks it.

**Interview line**: "@Component marks a class as a Spring-managed bean, auto-detected during component scanning."

---

## 5. @Service, @Repository, @Controller (specialized @Component)

**The problem**: `@Component` is generic — it doesn't show a class's *role*.

**The fix**: Specialized stereotypes that ARE @Components but add meaning (and sometimes behavior):

| Annotation | Role | Bonus behavior |
|-----------|------|----------------|
| `@Service` | Business logic | Just semantic clarity |
| `@Repository` | Data access | Converts DB exceptions to `DataAccessException` |
| `@Controller` | Web (returns views) | View resolution |
| `@RestController` | REST API | Returns JSON directly |

**Example**:
```java
@Service      public class OrderService { }        // business logic
@Repository   public interface OrderRepo { }        // DB access
@RestController public class OrderController { }     // REST endpoints
```

**Analogy**: All are employees (@Component), but with job titles — chef (@Service), storekeeper (@Repository), waiter (@Controller).

**Interview line**: "All three are @Components with specific roles. @Repository adds exception translation, @Controller/@RestController handle web requests. They make the architecture self-documenting."

---

## 6. @Bean

**The problem**: You want a class as a bean, but you CAN'T add `@Component` to it (it's a third-party library class you don't own).

**The fix**: Define it manually with a `@Bean` method inside a `@Configuration` class.

**Example (from your project)**:
```java
@Configuration
public class AiConfig {
    @Bean
    public ChatClient chatClient(OllamaChatModel model) {
        return ChatClient.builder(model).build();   // ChatClient is a library class
    }
}
```

**Analogy**: You can't put a sticker on someone else's tool, so you write a note telling the warehouse manager "build this one for me like this."

**Interview line**: "@Bean creates a bean from a method — used for third-party classes I can't annotate, or when I need custom construction logic. @Component is for my own classes."

---

## 7. @Configuration

**The problem**: Where do `@Bean` methods live?

**The fix**: In a class marked `@Configuration` — it's a source of bean definitions.

**Example**:
```java
@Configuration
public class AppConfig {
    @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
    @Bean public RestTemplate restTemplate() { return new RestTemplate(); }
}
```

**Interview line**: "@Configuration marks a class that defines beans via @Bean methods. Spring processes it to register those beans."

---

## 8. @Autowired

**The problem**: How do you tell Spring "inject a dependency here"?

**The fix**: `@Autowired`. Spring finds a matching bean by type and injects it.

**Example**:
```java
@Service
public class OrderService {
    @Autowired
    private PaymentService payment;   // Spring injects the PaymentService bean
}
```

Note: On constructors it's optional since Spring 4.3 if there's only one constructor.

**Analogy**: Raising your hand saying "I need a PaymentService" — Spring hands you one.

**Interview line**: "@Autowired tells Spring to inject a matching bean by type. I prefer it on constructors (or omit it entirely since it's optional for single constructors)."

---

## 9. @Qualifier

**The problem**: Two beans of the same type exist — Spring can't decide which to inject (`NoUniqueBeanDefinitionException`).

**The fix**: `@Qualifier("beanName")` names the exact bean you want.

**Example**:
```java
@Bean public DataSource mainDb() { ... }
@Bean public DataSource backupDb() { ... }

@Service
public class ReportService {
    public ReportService(@Qualifier("backupDb") DataSource ds) { }  // pick backupDb
}
```

**Analogy**: Kitchen has coffee AND tea. You say "I want tea specifically."

**Interview line**: "@Qualifier resolves ambiguity when multiple beans of the same type exist by naming the exact one to inject."

---

## 10. @Primary

**The problem**: Same ambiguity, but you want a default choice without qualifying everywhere.

**The fix**: Mark ONE bean `@Primary` — it wins when no qualifier is given.

**Example**:
```java
@Bean @Primary public DataSource mainDb() { ... }   // default
@Bean public DataSource backupDb() { ... }

// gets mainDb automatically:
public OrderService(DataSource ds) { }
```

**Analogy**: The menu marks coffee as "house default" — you get it unless you ask otherwise.

**Interview line**: "@Primary marks the default bean when multiple candidates exist. @Qualifier overrides it for specific cases."

---

## 11. @Value

**The problem**: You need a config value (from application.yml) inside your code.

**The fix**: `@Value("${property.name}")` injects it.

**Example (from your project)**:
```java
@Service
public class CacheService {
    public CacheService(@Value("${app.cache.response-ttl-minutes:60}") int ttl) {
        // ttl = value from yml, or 60 if missing
    }
}
```

**Analogy**: Reading a setting from a settings file and plugging it in.

**Interview line**: "@Value injects a property from config into a field or parameter, with an optional default after the colon."

---

## 12. @ConfigurationProperties

**The problem**: Many related `@Value` fields is messy and error-prone.

**The fix**: Bind a whole group of properties to a typed object.

**Example**:
```java
@ConfigurationProperties(prefix = "app.cache")
public record CacheProps(int responseTtlMinutes, int rateLimitRequestsPerMinute) {}
// binds app.cache.response-ttl-minutes and app.cache.rate-limit-requests-per-minute
```

**Interview line**: "@ConfigurationProperties binds a group of related properties to a typed class — cleaner than many @Value annotations, and type-safe."

---

# PART 3: THE APPLICATION SETUP

---

## 13. @SpringBootApplication

**The problem**: A Spring app needs configuration, auto-config, and component scanning — three separate annotations.

**The fix**: `@SpringBootApplication` bundles all three:
- `@Configuration` — can define beans
- `@EnableAutoConfiguration` — auto-configures based on classpath
- `@ComponentScan` — scans this package + sub-packages

**Example**:
```java
@SpringBootApplication
public class ContractAnalyserApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractAnalyserApplication.class, args);
    }
}
```

**Analogy**: A "start everything" master switch that turns on config, auto-setup, and scanning at once.

**Interview line**: "@SpringBootApplication combines @Configuration, @EnableAutoConfiguration, and @ComponentScan. It's the entry point that bootstraps the whole app."

---

## 14. Auto-Configuration

**The problem**: Configuring beans for every library (DataSource, Redis, Kafka) manually is tedious.

**The fix**: Spring Boot looks at your classpath and auto-creates sensible beans. Add a starter → get working defaults.

**Example**: Add `spring-boot-starter-data-redis` + set `spring.data.redis.host` → Spring auto-creates a `RedisConnectionFactory`. You didn't write any config.

**How it works**: Conditional beans — `@ConditionalOnClass` (library present?), `@ConditionalOnMissingBean` (you didn't define your own?), `@ConditionalOnProperty` (property set?).

**Analogy**: A smart home that auto-configures itself based on which appliances you plug in.

**Interview line**: "Auto-configuration detects libraries on the classpath and creates default beans conditionally. If I define my own bean, Spring backs off (@ConditionalOnMissingBean)."

---

## 15. Starters (spring-boot-starter-*)

**The problem**: Picking compatible versions of many libraries is painful ("dependency hell").

**The fix**: A starter is a curated bundle of dependencies that work together. One dependency pulls in everything needed.

**Example**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<!-- brings in reactor, netty, jackson, etc. — all compatible -->
```

**Analogy**: A meal combo instead of ordering each ingredient separately.

**Interview line**: "Starters bundle compatible dependencies. One starter pulls in a whole feature (web, data-jpa, redis) with version compatibility handled for me."

---

## 16. application.yml / application.properties

**The problem**: Hardcoding config (ports, URLs, passwords) in code is bad.

**The fix**: Externalize config into `application.yml` (or `.properties`). Spring reads it automatically.

**Example (from your project)**:
```yaml
server:
  port: 8080
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/contractdb}
```

**`${VAR:default}`**: use env var if present, else the default.

**Interview line**: "Config lives in application.yml, externalized from code. `${VAR:default}` reads an env var with a fallback — great for different environments."

---

## 17. Spring Profiles

**The problem**: Different settings for dev, test, prod (different DBs, secrets).

**The fix**: Profile-specific files `application-{profile}.yml`, activated by `spring.profiles.active`.

**Example (from your project)**:
```
application.yml         → default (local/docker)
application-gcp.yml     → GCP (fetches secrets)
```
```bash
java -jar app.jar --spring.profiles.active=gcp
```

**Analogy**: Different outfits for different occasions — same person, different config.

**Interview line**: "Profiles let me have environment-specific configs. `application-gcp.yml` activates only with the gcp profile, so I use one build across all environments."

---

# PART 4: WEB LAYER

---

## 18. @RestController & @RequestMapping

**The problem**: How do incoming HTTP requests reach your Java methods?

**The fix**: `@RestController` marks a class as a REST handler; mapping annotations bind URLs to methods.

**Example (from your project)**:
```java
@RestController
@RequestMapping("/api")           // base path
public class ChatController {

    @PostMapping("/chat/stream")   // POST /api/chat/stream
    public Flux<String> chat(@RequestBody ChatRequest req) { ... }

    @GetMapping("/chat/history")   // GET /api/chat/history
    public Mono<List<String>> history(@RequestParam Long userId) { ... }
}
```

**Interview line**: "@RestController handles REST requests and returns JSON. @RequestMapping/@GetMapping/@PostMapping bind URLs to methods."

---

## 19. Request Binding: @RequestBody, @RequestParam, @PathVariable, @RequestPart

**The problem**: How do you extract data from the request?

**The fix**: Different annotations for different parts:

```java
// JSON body → object
@PostMapping("/chat")
public X chat(@RequestBody ChatRequest req) { }

// Query param: /users?id=5
@GetMapping("/users")
public X get(@RequestParam Long id) { }

// URL path: /users/5
@GetMapping("/users/{id}")
public X get(@PathVariable Long id) { }

// File upload
@PostMapping("/upload")
public X upload(@RequestPart("file") FilePart file) { }
```

**Interview line**: "@RequestBody binds JSON body, @RequestParam binds query params, @PathVariable binds URL path segments, @RequestPart binds multipart file uploads."

---

## 20. @ResponseBody & ResponseEntity

**The problem**: How do you control the HTTP response (status code, headers, body)?

**The fix**: `@ResponseBody` (built into @RestController) writes the return value as JSON. `ResponseEntity` gives full control over status + headers + body.

**Example**:
```java
@GetMapping("/users/{id}")
public ResponseEntity<User> get(@PathVariable Long id) {
    User u = service.find(id);
    if (u == null) return ResponseEntity.notFound().build();       // 404
    return ResponseEntity.ok(u);                                   // 200 + body
}
```

**Interview line**: "@ResponseBody serializes the return value to JSON. ResponseEntity lets me set the status code and headers explicitly."

---

## 21. Global Exception Handling: @RestControllerAdvice + @ExceptionHandler

**The problem**: Try-catch in every controller method is repetitive and messy.

**The fix**: Centralize error handling in one class.

**Example**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404).body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAll(Exception e) {
        return ResponseEntity.status(500).body("Something went wrong");
    }
}
```

**Analogy**: One customer-complaints desk for the whole store instead of every employee handling complaints differently.

**Interview line**: "@RestControllerAdvice with @ExceptionHandler centralizes exception handling across all controllers, returning consistent error responses."

---

# PART 5: DATA & TRANSACTIONS

---

## 22. Spring Data JPA & Repositories

**The problem**: Writing boilerplate CRUD SQL for every entity is tedious.

**The fix**: Extend `JpaRepository` — Spring auto-generates the implementation, including queries derived from method names.

**Example**:
```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByDepartment(String dept);   // auto-generated SQL!
    Optional<Employee> findByEmail(String email);
}
// No implementation needed — Spring writes it.
```

**Analogy**: You describe the query in the method name; Spring writes the SQL for you.

**Interview line**: "Spring Data JPA generates repository implementations automatically. Method names like findByEmail become queries — no boilerplate SQL."

---

## 23. @Transactional

**The problem**: A business operation touches multiple DB rows. If one step fails midway, you get inconsistent data.

**The fix**: `@Transactional` wraps the method — all operations commit together or roll back together (atomicity).

**Example**:
```java
@Transactional
public void transferMoney(Long from, Long to, double amt) {
    accountRepo.debit(from, amt);    // step 1
    accountRepo.credit(to, amt);     // step 2
    // if step 2 throws → step 1 is rolled back automatically
}
```

**Two traps to mention**:
- Doesn't work on **private** or **self-invoked** methods (proxy-based).
- Rolls back only on **unchecked** exceptions by default (use `rollbackFor` for checked).

**Analogy**: A bank transfer — either both the debit AND credit happen, or neither. Never just one.

**Interview line**: "@Transactional guarantees atomicity — all-or-nothing. It's proxy-based, so it only works on public methods called from outside, and rolls back on runtime exceptions by default."

---

## 24. Transaction Propagation

**The problem**: A transactional method calls another transactional method. Do they share one transaction or separate ones?

**The fix**: Propagation controls this.

| Propagation | Behavior |
|-------------|----------|
| REQUIRED (default) | Join existing, or create new |
| REQUIRES_NEW | Always a new independent transaction |
| SUPPORTS | Use one if exists, else none |
| MANDATORY | Must have existing, else error |
| NEVER | Must NOT have one, else error |
| NESTED | Nested with savepoint |

**Example**:
```java
@Transactional
public void placeOrder(Order o) {
    orderRepo.save(o);
    auditLog();   // runs in its OWN transaction
}
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void auditLog() { auditRepo.save(...); }  // survives even if placeOrder rolls back
```

**Interview line**: "Propagation defines how nested transactional methods interact. REQUIRED joins the caller's transaction; REQUIRES_NEW starts an independent one — useful for audit logs that must persist regardless."

---

## 25. @Entity, @Id, @Column, Relationships

**The problem**: How do Java objects map to DB tables?

**The fix**: JPA annotations.

**Example**:
```java
@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "dept_id")
    private Department department;
}
```

**Interview line**: "@Entity maps a class to a table, @Id marks the primary key, @Column maps fields, and @ManyToOne/@OneToMany define relationships."

---

# PART 6: RESILIENCE & CROSS-CUTTING

---

## 26. @Scope (Bean Scopes)

**The problem**: Sometimes you need one shared instance, sometimes a fresh one each time.

**The fix**: `@Scope` controls this. Default is singleton.

**Example**:
```java
@Service                       // singleton (default) — one shared instance
public class RagService { }

@Service
@Scope("prototype")            // new instance every injection
public class ReportBuilder { }
```

**Interview line**: "Default scope is singleton — one instance for the app. Prototype gives a new instance each time. There are also web scopes: request, session."

---

## 27. Bean Lifecycle: @PostConstruct & @PreDestroy

**The problem**: You need to run setup code after a bean is built, or cleanup before shutdown.

**The fix**: Lifecycle callbacks.

**Example**:
```java
@Service
public class CacheWarmer {
    @PostConstruct
    public void init() { loadCacheOnStartup(); }   // after bean is ready

    @PreDestroy
    public void cleanup() { flushCache(); }         // before shutdown
}
```

**Interview line**: "@PostConstruct runs after dependencies are injected — good for initialization. @PreDestroy runs before shutdown — good for cleanup."

---

## 28. Spring AOP (Aspect-Oriented Programming)

**The problem**: Cross-cutting concerns (logging, security, transactions) repeat across many methods.

**The fix**: AOP lets you write that logic once (an "aspect") and apply it automatically. This is how `@Transactional` works internally (via proxies).

**Example**:
```java
@Aspect
@Component
public class LoggingAspect {
    @Before("execution(* com.app.service.*.*(..))")
    public void logBefore(JoinPoint jp) {
        System.out.println("Calling: " + jp.getSignature());
    }
}
```

**Analogy**: Security cameras applied to every room automatically — you don't install one manually in each.

**Interview line**: "AOP extracts cross-cutting concerns like logging and transactions into reusable aspects, applied via proxies. @Transactional and @CircuitBreaker use AOP under the hood."

---

## 29. @CircuitBreaker (Resilience4j)

**The problem**: A downstream service is down. Repeatedly calling it hangs your app and cascades the failure.

**The fix**: A circuit breaker detects failures and "opens" — rejecting calls instantly and returning a fallback.

**Example (from your project)**:
```java
@CircuitBreaker(name = "auditService", fallbackMethod = "auditFallback")
public Mono<String> logAudit(...) {
    return webClient.post()...;
}
private Mono<String> auditFallback(..., Throwable t) {
    return Mono.just("Audit service unavailable");
}
```

**States**: CLOSED (normal) → OPEN (failing, reject) → HALF_OPEN (test recovery).

**Interview line**: "A circuit breaker stops calling a failing service to prevent cascading failures. After failures cross a threshold it opens and returns a fallback, then tests recovery."

---

## 30. Actuator (Health & Metrics)

**The problem**: How do you monitor if the app is healthy in production?

**The fix**: `spring-boot-starter-actuator` exposes endpoints like `/actuator/health`, `/actuator/metrics`.

**Example**:
```
GET /actuator/health  →  {"status": "UP"}
```
Used by Kubernetes readiness/liveness probes.

**Interview line**: "Actuator exposes health and metrics endpoints. Kubernetes uses /actuator/health for readiness and liveness probes."

---

# PART 7: MORE SPRING BOOT CONCEPTS (don't miss these)

---

## 31. @Lazy (Lazy Initialization)

**The problem**: Some beans are heavy to create but rarely used, or you have a circular dependency at startup.

**The fix**: `@Lazy` delays creating the bean until it's first actually needed.

**Real example**:
```java
@Service
public class ReportService {
    private final HeavyPdfEngine engine;
    public ReportService(@Lazy HeavyPdfEngine engine) {   // created only on first use
        this.engine = engine;
    }
}
```

**Real scenario**: A PDF-export engine that takes 3 seconds to initialize but is used only when a user clicks "Export." @Lazy skips that cost at startup.

**Interview line**: "@Lazy delays bean creation until first use — saves startup time for heavy, rarely-used beans and can break circular dependencies."

---

## 32. @Conditional / @ConditionalOnProperty

**The problem**: You want a bean to exist only in certain conditions (a property is set, a class is present, an environment).

**The fix**: Conditional annotations create the bean only if the condition is true.

**Real example**:
```java
@Bean
@ConditionalOnProperty(name = "feature.email.enabled", havingValue = "true")
public EmailService emailService() {
    return new EmailService();
}
```

**Real scenario**: Only create the `EmailService` bean if `feature.email.enabled=true` in config. In dev you turn it off; in prod you turn it on — same code.

**Interview line**: "@ConditionalOnProperty creates a bean only when a config property matches — great for feature toggles across environments. Spring Boot's own auto-config uses these conditions heavily."

---

## 33. @Profile

**The problem**: You need different beans for different environments (a fake email sender in dev, a real one in prod).

**The fix**: `@Profile` activates a bean only under a given profile.

**Real example**:
```java
@Service
@Profile("dev")
public class FakeEmailService implements EmailService {   // logs instead of sending
    public void send(String to, String msg) { log.info("FAKE email to " + to); }
}

@Service
@Profile("prod")
public class RealEmailService implements EmailService {   // actually sends
    public void send(String to, String msg) { smtpClient.send(to, msg); }
}
```

**Real scenario**: In dev you don't want to spam real inboxes, so the "dev" profile wires the fake sender. Activated by `spring.profiles.active=dev`.

**Interview line**: "@Profile activates a bean only for a specific environment. I use it to swap a fake email service in dev for a real one in prod without code changes."

---

## 34. @Scheduled (Scheduled Tasks)

**The problem**: You need to run a task repeatedly on a schedule (cleanup, reports, syncs).

**The fix**: `@EnableScheduling` + `@Scheduled` runs a method automatically on a timer or cron.

**Real example**:
```java
@Component
@EnableScheduling
public class CacheCleaner {
    @Scheduled(cron = "0 0 2 * * *")     // every day at 2 AM
    public void purgeOldCache() {
        cacheRepo.deleteOlderThan(30);
    }

    @Scheduled(fixedRate = 60000)         // every 60 seconds
    public void heartbeat() { log.info("alive"); }
}
```

**Real scenario**: Delete expired sessions every night at 2 AM automatically — no manual trigger.

**Interview line**: "@Scheduled runs methods on a timer or cron. I use it for periodic jobs like nightly cleanup — fixedRate for intervals, cron for specific times."

---

## 35. @Async (Asynchronous Execution)

**The problem**: A slow task (sending email, generating a report) blocks the user's request.

**The fix**: `@EnableAsync` + `@Async` runs the method on a separate thread; the caller doesn't wait.

**Real example**:
```java
@Service
@EnableAsync
public class EmailService {
    @Async
    public void sendWelcomeEmail(String to) {
        // takes 3 seconds — runs in background
        smtpClient.send(to, "Welcome!");
    }
}
// Caller: register user → return response immediately, email sends in background
```

**Real scenario**: User signs up → you respond "Success" instantly while the welcome email sends in the background.

**Interview line**: "@Async runs a method on a background thread so the caller isn't blocked — I use it for fire-and-forget tasks like sending emails after signup."

---

## 36. @Cacheable / @CacheEvict (Spring Caching)

**The problem**: An expensive method (DB query, API call) is called repeatedly with the same inputs.

**The fix**: `@EnableCaching` + `@Cacheable` stores the result; repeat calls with the same args return the cached value.

**Real example**:
```java
@Service
@EnableCaching
public class ProductService {

    @Cacheable("products")
    public Product getProduct(Long id) {
        return repo.findById(id);   // hits DB only the FIRST time for each id
    }

    @CacheEvict(value = "products", key = "#id")
    public void updateProduct(Long id, Product p) {
        repo.save(p);               // clears cache so next read is fresh
    }
}
```

**Real scenario**: Product details are read thousands of times but rarely change. First read hits the DB; the rest come from cache instantly. On update, evict so stale data isn't served.

**Interview line**: "@Cacheable caches a method's result by its arguments; @CacheEvict clears it on update. It's declarative caching — I annotate the method instead of writing cache logic."

---

## 37. Bean Validation (@Valid, @NotNull, @Size)

**The problem**: Bad input (null name, negative age, invalid email) reaches your logic and causes errors.

**The fix**: Annotate fields with constraints; `@Valid` triggers validation automatically, rejecting bad requests.

**Real example**:
```java
public record UserRequest(
    @NotBlank String name,
    @Email String email,
    @Min(18) int age
) {}

@PostMapping("/users")
public User create(@Valid @RequestBody UserRequest req) {   // @Valid enforces rules
    return service.save(req);
}
// If email is invalid → 400 Bad Request automatically, before your code runs
```

**Real scenario**: A signup form. If age < 18 or email is malformed, Spring rejects it with a 400 before it ever touches your service.

**Interview line**: "Bean Validation with @Valid enforces field constraints like @NotBlank and @Email automatically. Invalid requests get rejected with 400 before hitting my business logic."

---

## 38. Spring Data JPA — @Query & @Modifying

**The problem**: Method-name queries can't express complex custom SQL/JPQL.

**The fix**: Write your own query with `@Query`; use `@Modifying` for updates/deletes.

**Real example**:
```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("SELECT e FROM Employee e WHERE e.salary > :min AND e.dept = :dept")
    List<Employee> findHighEarners(@Param("min") double min, @Param("dept") String dept);

    @Modifying
    @Query("UPDATE Employee e SET e.designation = :title WHERE e.id = :id")
    int updateDesignation(@Param("id") Long id, @Param("title") String title);
}
```

**Real scenario**: "Find all employees earning over 50k in Engineering" — too complex for a method name, so use @Query.

**Interview line**: "@Query lets me write custom JPQL/SQL when method-name derivation isn't enough. @Modifying marks update/delete queries."

---

## 39. CommandLineRunner / ApplicationRunner

**The problem**: You need to run some code once, right after the app starts (seed data, warm cache, print info).

**The fix**: Implement `CommandLineRunner` — its `run()` executes after startup.

**Real example**:
```java
@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository repo;
    public DataSeeder(UserRepository repo) { this.repo = repo; }

    @Override
    public void run(String... args) {
        if (repo.count() == 0) {
            repo.save(new User("admin", "admin@app.com"));   // seed default admin
        }
    }
}
```

**Real scenario**: On first startup, create a default admin user if the DB is empty.

**Interview line**: "CommandLineRunner runs code once after startup — I use it to seed initial data or warm up caches."

---

## 40. @EventListener (Application Events)

**The problem**: When something happens (order placed), several unrelated things must react (send email, update stats) — but coupling them is messy.

**The fix**: Publish an event; listeners react independently (in-app observer pattern).

**Real example**:
```java
// Publish
@Service
public class OrderService {
    private final ApplicationEventPublisher publisher;
    public void placeOrder(Order o) {
        repo.save(o);
        publisher.publishEvent(new OrderPlacedEvent(o));   // announce it
    }
}

// React (separate class, no coupling)
@Component
public class EmailNotifier {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent e) {
        emailService.sendConfirmation(e.getOrder());
    }
}
```

**Real scenario**: Order placed → one event → email notifier and inventory updater both react, without OrderService knowing about them.

**Interview line**: "@EventListener decouples reactions from the action. OrderService just publishes an event; multiple listeners handle emailing, stats, etc. independently."

---

## 41. Filter vs Interceptor

**The problem**: You want to run logic on EVERY request (logging, auth, headers) without repeating it.

**The fix**: 
- **Filter** (Servlet level) — runs before the request reaches Spring (raw request/response).
- **Interceptor** (Spring MVC level) — runs around controller methods (has Spring context).

**Real example (Filter)**:
```java
@Component
public class RequestLoggingFilter implements Filter {
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        long start = System.currentTimeMillis();
        chain.doFilter(req, res);                        // continue the chain
        log.info("Took {} ms", System.currentTimeMillis() - start);
    }
}
```

**Real scenario**: Log every request's duration, or check a JWT token before it reaches any controller.

**Interview line**: "Filters run at the servlet level before Spring; interceptors run at the MVC level around controllers. I'd use a filter for JWT auth/logging, an interceptor when I need Spring's handler context."

---

## 42. @CrossOrigin (CORS)

**The problem**: A browser blocks JavaScript on `localhost:3000` from calling your API on `localhost:8000` (different origin).

**The fix**: `@CrossOrigin` (or a global CORS config) allows specific origins.

**Real example (from your project — global version)**:
```java
@Bean
public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000"));   // allow the React app
    config.setAllowedMethods(List.of("GET", "POST"));
    ...
}
// Or per-controller:
@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class ChatController { }
```

**Real scenario**: Your React frontend (port 3000) calling your Spring backend (port 8000) — CORS config lets the browser allow it.

**Interview line**: "CORS controls which origins can call my API. In my project I use a global CorsWebFilter to allow my React frontend's origin."

---

## 43. RestTemplate vs WebClient (calling other services)

**The problem**: Your service needs to call another REST API.

**The fix**:
- **RestTemplate** — blocking, synchronous (older, Spring MVC).
- **WebClient** — non-blocking, reactive (modern, WebFlux).

**Real example (WebClient — from your project)**:
```java
webClient.post()
    .uri("/api/audit/log")
    .bodyValue(auditData)
    .retrieve()
    .bodyToMono(String.class);   // non-blocking
```

**Real scenario**: Your backend calls the audit microservice. Since your app is reactive, you use WebClient.

**Interview line**: "RestTemplate is blocking; WebClient is non-blocking and reactive. My project uses WebClient because it's built on WebFlux and I call the audit service asynchronously."

---

## 44. Spring Security Basics (Authentication & Authorization)

**The problem**: You need to control who can access your endpoints and what they can do.

**The fix**: Spring Security handles authentication (who you are) and authorization (what you're allowed).

**Real example**:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()      // open
                .requestMatchers("/api/admin/**").hasRole("ADMIN")  // admin only
                .anyRequest().authenticated())                      // rest need login
            .oauth2ResourceServer(oauth -> oauth.jwt());            // JWT auth
        return http.build();
    }
}
```

**Real scenario**: `/api/public` is open, `/api/admin` needs ADMIN role, everything else needs a valid JWT token.

**Interview line**: "Spring Security manages authentication and authorization via a filter chain. I define which endpoints are public, which need roles, and typically use JWT for stateless auth."

---

## 45. Embedded Server & Fat JAR

**The problem**: Traditionally you built a WAR and deployed it to an external Tomcat — extra setup, version mismatches.

**The fix**: Spring Boot embeds the server (Tomcat/Netty) INSIDE the app. `mvn package` builds one runnable "fat JAR" with everything.

**Real example**:
```bash
mvn clean package
java -jar app.jar        # server is inside — just run the JAR
```

**Real scenario**: Your Dockerfile just does `java -jar app.jar` — no separate Tomcat to install. This is why containerization is so clean.

**Interview line**: "Spring Boot embeds the web server, so `java -jar app.jar` runs everything — no external Tomcat. That's why it's ideal for Docker; my Dockerfile just runs the fat JAR."

---

# PART 8: DESIGN PATTERNS (GoF + Microservices)

Each: Problem → Fix → Example → Analogy → Interview line.

---

## 46. Singleton (Creational)

**Problem**: You want exactly ONE shared instance (config, connection pool). Multiple copies waste memory or cause inconsistency.

**Fix**: Restrict creation to one instance.

**Real example**:
```java
// Every Spring @Service is a singleton by default:
@Service
public class RagService { }   // ONE instance shared by all requests
```

**Analogy**: A country has ONE president — everyone refers to the same person.

**Interview line**: "Singleton = one shared instance. Spring beans are singletons by default."

---

## 47. Factory (Creational)

**Problem**: You need to create objects without the caller knowing the concrete class.

**Fix**: A factory method decides which class to instantiate.

**Real example**:
```java
class NotificationFactory {
    static Notification create(String type) {
        return switch (type) {
            case "EMAIL" -> new EmailNotification();
            case "SMS"   -> new SmsNotification();
            default -> throw new IllegalArgumentException();
        };
    }
}
Notification n = NotificationFactory.create("EMAIL");
```

**Analogy**: Order "a pizza" at the counter — the kitchen decides how to make it.

**Interview line**: "Factory centralizes object creation and hides the concrete class. Spring's BeanFactory is a real example."

---

## 48. Builder (Creational)

**Problem**: Objects with many optional fields lead to messy constructors.

**Fix**: Build step by step with a fluent chain, then `build()`.

**Real example (from your project)**:
```java
ChatClient client = ChatClient.builder(chatModel).build();
SearchRequest req = SearchRequest.query(question).withTopK(3).withFilterExpression(f);
```

**Analogy**: Building a Subway sandwich — add ingredients one at a time.

**Interview line**: "Builder constructs complex objects step by step with a readable API. I use ChatClient.builder() in my project."

---

## 49. Prototype (Creational)

**Problem**: Creating an object from scratch is expensive.

**Fix**: Clone an existing one.

**Real example**:
```java
class Config implements Cloneable {
    public Config clone() throws CloneNotSupportedException {
        return (Config) super.clone();
    }
}
```

**Analogy**: A photocopy — faster than rewriting the whole document.

**Interview line**: "Prototype creates objects by cloning an existing instance when construction is costly."

---

## 50. Adapter (Structural)

**Problem**: Two incompatible interfaces must work together.

**Fix**: A wrapper translates one interface to another.

**Real example**:
```java
class PrinterAdapter implements NewPrinter {
    private OldPrinter old = new OldPrinter();
    public void print(String s) { old.printOld(s); }   // translate call
}
```

**Analogy**: A travel plug adapter — Indian charger into a US socket.

**Interview line**: "Adapter converts one interface into another so incompatible classes cooperate — like a plug adapter."

---

## 51. Decorator (Structural)

**Problem**: Add features to an object dynamically without subclassing.

**Fix**: Wrap the object in a decorator that adds behavior.

**Real example**:
```java
BufferedReader r = new BufferedReader(new FileReader("f.txt"));
//                  ↑ adds buffering    ↑ base object
```

**Analogy**: Adding toppings to ice cream — each wraps and enhances the base.

**Interview line**: "Decorator adds behavior by wrapping an object. Java's BufferedReader wrapping FileReader is the classic example."

---

## 52. Proxy (Structural)

**Problem**: Control access to an object — add logging, security, transactions — without changing it.

**Fix**: A proxy stands in and adds behavior around calls.

**Real example (from your project)**:
```java
@Transactional              // Spring wraps this in a proxy:
public void save(Order o) { // begin tx → run → commit/rollback
    repo.save(o);
}
```

**Analogy**: A celebrity's manager controls access and handles extra tasks.

**Interview line**: "Proxy controls access and adds cross-cutting behavior. Spring uses proxies for @Transactional and @CircuitBreaker — that's why they only work on public methods."

---

## 53. Facade (Structural)

**Problem**: A complex subsystem overwhelms clients with many classes.

**Fix**: One simple interface hides the complexity.

**Real example (from your project)**:
```java
// RagService.streamResponse() hides: rate-limit → cache → embed → search → LLM
ragService.streamResponse(request);   // client calls ONE method
```

**Analogy**: A universal remote — one button hides all the complex signals.

**Interview line**: "Facade gives a simple entry point over complex subsystems. My RagService.streamResponse hides all the RAG steps behind one call."

---

## 54. Strategy (Behavioral)

**Problem**: Multiple algorithms for a task; you want to swap them without changing the caller.

**Fix**: Common interface; each algorithm is an implementation; inject the one you need.

**Real example (from your project)**:
```java
// VectorStore is a strategy interface:
private final VectorStore vectorStore;   // PgVectorStore now, could swap to Chroma
```

**Analogy**: Google Maps route options — car, walk, transit. Same goal, swappable strategy.

**Interview line**: "Strategy swaps algorithms behind a common interface. My VectorStore is a strategy — I can change the implementation via config without touching RagService."

---

## 55. Observer (Behavioral)

**Problem**: When one thing changes, many others must react — without tight coupling.

**Fix**: Subscribers listen to a subject; it notifies them on change.

**Real example (from your project)**:
```java
kafkaTemplate.send("contract-audit-topic", event);   // notify all consumers
// The audit service (observer) consumes and reacts independently
```

**Analogy**: YouTube subscriptions — creator uploads, all subscribers get notified.

**Interview line**: "Observer notifies subscribers on change with loose coupling. My Kafka producer/consumer is an observer pattern."

---

## 56. Template Method (Behavioral)

**Problem**: Several processes share the same steps but differ in details.

**Fix**: Base defines the skeleton; you fill in specific steps.

**Real example (from your project)**:
```java
jdbcTemplate.query("SELECT * FROM users", rowMapper);
// Template handles connection open/close; you supply query + mapping
```

**Analogy**: A recipe template — fixed steps, you fill in ingredients.

**Interview line**: "Template Method fixes the algorithm skeleton and lets steps vary. Spring's JdbcTemplate handles boilerplate; I supply only the query."

---

## 57. Chain of Responsibility (Behavioral)

**Problem**: A request should pass through multiple handlers, each deciding to handle or forward.

**Fix**: Chain handlers; each processes or passes on.

**Real example (from your project)**:
```java
// RagService flow: rate-limit check → cache check → RAG pipeline
// each step decides pass-through or stop
```

**Analogy**: Support escalation — Level 1 → Level 2 → Manager.

**Interview line**: "Chain of Responsibility passes a request through handlers. My RAG flow chains rate-limit → cache → LLM, each deciding to handle or pass on."

---

## 58. API Gateway (Microservices)

**Problem**: Clients would call many services directly and handle auth for each.

**Fix**: Single entry point that routes and handles cross-cutting concerns.

**Real example**:
```
Client → API Gateway → /orders/*   → Order Service
                     → /payments/* → Payment Service
```

**Analogy**: Hotel reception — one desk routes you to the right department.

**Interview line**: "API Gateway is one entry point that routes to services and centralizes auth and rate limiting."

---

## 59. Service Discovery (Microservices)

**Problem**: Service IPs change as instances scale; hardcoding breaks.

**Fix**: Services register with a registry (Eureka); others look them up dynamically.

**Real example**:
```
Order Service → asks Eureka "where's Payment Service?" → gets healthy instance
```

**Analogy**: A phone directory — look up the current number.

**Interview line**: "Service Discovery lets services find each other dynamically via a registry instead of hardcoded addresses."

---

## 60. Circuit Breaker (Microservices)

**Problem**: A down service causes hangs and cascading failure.

**Fix**: Open the circuit after failures — reject instantly, return fallback, test recovery.

**Real example (from your project)**:
```java
@CircuitBreaker(name = "auditService", fallbackMethod = "auditFallback")
public Mono<String> logAudit(...) { return webClient.post()...; }
```

**Analogy**: An electrical breaker trips to prevent a fire.

**Interview line**: "Circuit Breaker stops calling a failing service and returns a fallback, preventing cascading failure. I use Resilience4j on my audit calls."

---

## 61. Saga (Microservices)

**Problem**: Can't use one ACID transaction across services with separate DBs.

**Fix**: Sequence of local transactions, each with a compensating action to undo on failure.

**Real example**:
```
1. create order   (undo: cancel)
2. charge payment (undo: refund)
3. reserve stock  (undo: release)
Step 3 fails → refund + cancel
```

**Analogy**: Booking a trip — if the car fails, cancel hotel and flight.

**Interview line**: "Saga handles distributed transactions with local steps plus compensations. Choreography uses events; orchestration uses a central coordinator."

---

## 62. CQRS (Microservices)

**Problem**: One model for reads and writes becomes a bottleneck.

**Fix**: Separate write model (commands) from read model (queries), synced via events.

**Real example**:
```
Write: CreateOrder → Order DB → publishes event
Read:  GetHistory  → fast denormalized read DB ← updated by event
```

**Analogy**: A library — cataloging (write) is separate from browsing (read).

**Interview line**: "CQRS separates read and write models so each scales independently."

---

## 63. Bulkhead (Microservices)

**Problem**: One overloaded feature exhausts all threads/connections, crashing everything.

**Fix**: Isolate resources into separate pools.

**Real example**: Resilience4j `@Bulkhead` limits concurrent calls to one service so it can't starve others.

**Analogy**: A ship's watertight compartments — one flooded section doesn't sink the ship.

**Interview line**: "Bulkhead isolates resources into pools so one failure doesn't exhaust everything — like ship compartments."

---

## 64. Strangler Fig (Microservices)

**Problem**: Rewriting a huge monolith all at once is too risky.

**Fix**: Replace pieces incrementally, routing traffic to new services until the monolith is gone.

**Analogy**: A strangler fig vine slowly grows around a tree until it replaces it.

**Interview line**: "Strangler Fig migrates a monolith gradually — move features to new services one at a time, avoiding a risky big-bang rewrite."

---

# PATTERNS IN YOUR PROJECT (say these with confidence)

| Pattern | Where |
|---------|-------|
| Singleton | All @Service beans |
| Factory | Spring BeanFactory |
| Builder | ChatClient.builder(), SearchRequest |
| Proxy | @Transactional, @CircuitBreaker |
| Facade | RagService.streamResponse() |
| Strategy | VectorStore interface |
| Observer | Kafka producer/consumer |
| Template Method | JdbcTemplate, RedisTemplate |
| Chain of Responsibility | rate-limit → cache → LLM |
| Circuit Breaker | AuditService (Resilience4j) |

---

# MASTER SUMMARY TABLE

| Concept | One-liner | Analogy |
|---------|-----------|---------|
| IoC | Container controls object creation | Restaurant cooks for you |
| DI | Dependencies injected in | Factory installs engine |
| ApplicationContext | The IoC container | Warehouse manager |
| @Component | Marks a managed bean | "Manage me" sticker |
| @Service/@Repository/@Controller | Role-specific components | Job titles |
| @Bean | Manual bean (3rd-party) | Note to build a tool |
| @Configuration | Holds @Bean methods | Recipe book |
| @Autowired | Inject by type | Raise hand for a tool |
| @Qualifier | Pick specific bean by name | "I want tea" |
| @Primary | Default bean choice | "House default" |
| @Value | Inject one property | Read a setting |
| @ConfigurationProperties | Bind property group | Read a settings section |
| @SpringBootApplication | Config+AutoConfig+Scan | Master start switch |
| Auto-configuration | Auto beans from classpath | Smart home |
| Starters | Curated dependency bundles | Meal combo |
| application.yml | Externalized config | Settings file |
| Profiles | Env-specific config | Outfit per occasion |
| @RestController | REST handler (JSON) | Waiter taking orders |
| @RequestBody/@RequestParam/@PathVariable | Extract request data | Reading the order slip |
| ResponseEntity | Control status+headers | Custom receipt |
| @RestControllerAdvice | Global exception handler | Complaints desk |
| Spring Data JPA | Auto CRUD repositories | Describe query → get SQL |
| @Transactional | All-or-nothing atomicity | Bank transfer |
| Propagation | How nested tx interact | Shared vs separate contract |
| @Entity/@Id/@Column | Object-to-table mapping | Blueprint labels |
| @Scope | Instance count control | One vs many copies |
| @PostConstruct/@PreDestroy | Init/cleanup hooks | Setup/teardown |
| AOP | Reusable cross-cutting logic | Security cameras everywhere |
| @CircuitBreaker | Stop calling failed service | Circuit trips to prevent fire |
| Actuator | Health/metrics endpoints | Dashboard gauges |
| @Lazy | Delay bean creation until used | Load only when needed |
| @ConditionalOnProperty | Bean only if condition true | Feature toggle |
| @Profile | Bean per environment | Fake email in dev, real in prod |
| @Scheduled | Run task on timer/cron | Nightly cleanup at 2 AM |
| @Async | Run in background thread | Send email after signup |
| @Cacheable | Cache method result | Product details cached |
| @Valid | Validate request input | Reject bad signup form |
| @Query / @Modifying | Custom JPQL/SQL | Complex employee query |
| CommandLineRunner | Run code after startup | Seed default admin |
| @EventListener | React to app events | Order placed → email |
| Filter vs Interceptor | Run logic per request | JWT check / logging |
| @CrossOrigin (CORS) | Allow cross-origin calls | React 3000 → API 8000 |
| RestTemplate vs WebClient | Call other services | WebClient for reactive |
| Spring Security | Auth + authorization | Public / admin / JWT |
| Embedded server / fat JAR | Server inside the app | java -jar app.jar |

## Design Patterns Summary

| Pattern | Category | Analogy |
|---------|----------|---------|
| Singleton | Creational | One president |
| Factory | Creational | Pizza counter |
| Builder | Creational | Subway sandwich |
| Prototype | Creational | Photocopy |
| Adapter | Structural | Travel plug |
| Decorator | Structural | Ice cream toppings |
| Proxy | Structural | Celebrity manager |
| Facade | Structural | Universal remote |
| Strategy | Behavioral | Google Maps routes |
| Observer | Behavioral | YouTube subscriptions |
| Template Method | Behavioral | Recipe template |
| Chain of Responsibility | Behavioral | Support escalation |
| API Gateway | Microservices | Hotel reception |
| Service Discovery | Microservices | Phone directory |
| Circuit Breaker | Microservices | Electrical breaker |
| Saga | Microservices | Trip booking undo |
| CQRS | Microservices | Library catalog vs browse |
| Bulkhead | Microservices | Ship compartments |
| Strangler Fig | Microservices | Strangler vine |

---

*Every concept here uses the same pattern: Problem → Fix → Example → Analogy → Interview line. Master it and you can explain ANY Spring concept clearly.*
