# Interview Q&A — Round 2
## Java 8 · Optional · Design Patterns · Security · Spring · Multi-DB · Transactions · JPA · Docker · Deployment

Each answer = short explanation + small example you can speak out loud.

---

# 1. Java 8 Streams

**What**: Streams process collections in a declarative, functional way — filter, map, reduce — without manual loops.

**Key operations**:
- **Intermediate** (lazy, return a stream): `filter`, `map`, `sorted`, `distinct`
- **Terminal** (trigger execution): `collect`, `forEach`, `count`, `reduce`

**Example**:
```java
List<String> names = List.of("Riaan", "Anu", "Bala", "Anu");

List<String> result = names.stream()
    .distinct()                         // remove duplicates
    .filter(n -> n.length() > 3)        // keep long names
    .map(String::toUpperCase)           // transform
    .sorted()                           // sort
    .collect(Collectors.toList());
// [BALA, RIAAN]
```

**How to explain**: "Streams let me express *what* I want, not *how* to loop. They're lazy — nothing runs until a terminal operation like `collect`."

---

# 2. Optional

**What**: `Optional<T>` is a container that may or may not hold a value. It replaces `null` to avoid `NullPointerException`.

**Why**: Forces you to consciously handle the "no value" case.

**Example**:
```java
Optional<Employee> emp = repository.findById(1);

// Instead of: if (emp != null) ...
String name = emp.map(Employee::getName)
                 .orElse("Unknown");

emp.ifPresent(e -> System.out.println(e.getName()));  // run only if present
Employee e = emp.orElseThrow(() -> new NotFoundException());  // or throw
```

**How to explain**: "Optional makes the possibility of 'no result' explicit in the type system, so I don't forget null checks. I use `map`, `orElse`, `orElseThrow` instead of if-null."

---

# 3. Factory Pattern

**What**: A creational design pattern. A factory method decides which object (subclass) to create, hiding the instantiation logic from the caller.

**Why**: The caller asks for an object by type/name without knowing the concrete class.

**Example**:
```java
interface Notification { void send(String msg); }
class EmailNotification implements Notification { public void send(String m){...} }
class SmsNotification implements Notification { public void send(String m){...} }

class NotificationFactory {
    public static Notification create(String type) {
        return switch (type) {
            case "EMAIL" -> new EmailNotification();
            case "SMS"   -> new SmsNotification();
            default -> throw new IllegalArgumentException("Unknown type");
        };
    }
}
// Usage:
Notification n = NotificationFactory.create("EMAIL");
n.send("Hello");
```

**How to explain**: "The factory centralizes object creation. If I add a new notification type, I only change the factory — not every caller. Spring's `BeanFactory` is a real-world factory."

---

# 4. How would you secure your data? (Ketaan / security)

**Answer** — layers of security:

| Layer | How |
|-------|-----|
| **Secrets** | Store in GCP Secret Manager / Vault, never in code |
| **In transit** | HTTPS/TLS encryption |
| **At rest** | Encrypt DB & disk (AES-256) |
| **Authentication** | Spring Security + JWT tokens |
| **Authorization** | Role-based access control (RBAC) |
| **Input validation** | Prevent SQL injection (parameterized queries), XSS |
| **Rate limiting** | Redis counters to prevent abuse |
| **Passwords** | Hash with BCrypt (never plain text) |

**Example (from my project)**: I moved DB passwords out of config into **GCP Secret Manager**, fetched at runtime via `${sm://db-password}`. Access controlled by IAM.

**Example (password hashing)**:
```java
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hashed = encoder.encode("myPassword");   // store this
boolean matches = encoder.matches("myPassword", hashed);  // verify at login
```

---

# 5. Circuit Breaker

**What**: A resilience pattern that stops calling a failing service to prevent cascading failures. Uses Resilience4j.

**States**:
- **CLOSED** → normal, calls pass through
- **OPEN** → too many failures (>50%), calls rejected instantly for 30s
- **HALF_OPEN** → after 30s, test with a few calls; if OK → CLOSED, else → OPEN

**Example (from my project)**:
```java
@CircuitBreaker(name = "auditService", fallbackMethod = "auditFallback")
public Mono<String> logAudit(String contract, String status, int words) {
    return webClient.post().uri("/api/audit/log")
        .bodyValue(body).retrieve().bodyToMono(String.class);
}

private Mono<String> auditFallback(String c, String s, int w, Throwable t) {
    return Mono.just("Audit service unavailable");  // graceful degradation
}
```

**How to explain**: "If the audit service is down, the circuit opens and I return a fallback instantly instead of hanging. This keeps my main flow alive — graceful degradation."

---

# 6. Spring Boot Annotations

| Annotation | Purpose |
|-----------|---------|
| `@SpringBootApplication` | Main entry (auto-config + scan + config) |
| `@RestController` | REST API controller (returns JSON) |
| `@Service` | Business logic bean |
| `@Repository` | Data access bean |
| `@Component` | Generic bean |
| `@Configuration` + `@Bean` | Manual bean definition |
| `@Autowired` | Inject dependency |
| `@Value` | Inject property value |
| `@RequestMapping` / `@GetMapping` / `@PostMapping` | Map URLs |
| `@RequestBody` / `@RequestParam` / `@PathVariable` | Bind request data |
| `@Transactional` | Transaction management |
| `@CircuitBreaker` | Resilience4j fault tolerance |

---

# 7. Spring MVC

**What**: Spring's web framework following the Model-View-Controller pattern.
- **Model** → data
- **View** → UI (JSP/Thymeleaf) or JSON
- **Controller** → handles requests, returns response

**Flow**:
```
Request → DispatcherServlet → Controller → Service → Repository → DB
                                  ↓
Response ← View/JSON ← Controller
```

**Example**:
```java
@RestController
public class EmployeeController {
    @GetMapping("/employees/{id}")
    public Employee get(@PathVariable Long id) {
        return service.findById(id);   // returned as JSON
    }
}
```

---

# 8. Bean Scope

**What**: Defines how many instances of a bean Spring creates and their lifecycle.

| Scope | Meaning |
|-------|---------|
| **singleton** (default) | ONE instance for the whole app |
| **prototype** | New instance every time it's requested |
| **request** | One per HTTP request (web) |
| **session** | One per HTTP session (web) |
| **application** | One per ServletContext |

**Example**:
```java
@Service
@Scope("prototype")   // new instance each injection
public class ReportGenerator { }

@Service   // default singleton — one shared instance
public class RagService { }
```

**How to explain**: "By default beans are singletons — one shared instance, memory-efficient and thread-safe if stateless. I use prototype only when each caller needs its own stateful instance."

---

# 9. How to configure 2 different databases in one application?

**Answer**: Define two `DataSource` beans, each with its own `EntityManagerFactory` and `TransactionManager`, and mark one as `@Primary`.

**Step 1 — application.yml**:
```yaml
spring:
  datasource:
    primary:
      url: jdbc:postgresql://localhost:5432/maindb
      username: user1
      password: pass1
    secondary:
      url: jdbc:mysql://localhost:3306/reportdb
      username: user2
      password: pass2
```

**Step 2 — Config class for each DB**:
```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.app.primary.repo",
    entityManagerFactoryRef = "primaryEMF",
    transactionManagerRef = "primaryTM")
public class PrimaryDbConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean primaryEMF(
            EntityManagerFactoryBuilder builder, @Qualifier("primaryDataSource") DataSource ds) {
        return builder.dataSource(ds).packages("com.app.primary.entity").build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager primaryTM(@Qualifier("primaryEMF") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

**Step 3 — a second similar config** for the secondary DB (without `@Primary`), pointing to `com.app.secondary.repo`.

**How to explain**: "Each database gets its own DataSource, EntityManager, and TransactionManager. Repositories are separated by package so Spring knows which DB to route to. One is `@Primary` to resolve ambiguity."

---

# 10. Java 8 Features

| Feature | What it does | Example |
|---------|-------------|---------|
| **Lambda** | Anonymous functions | `(a,b) -> a+b` |
| **Streams** | Functional data processing | `list.stream().filter(...)` |
| **Functional interfaces** | Single-method interfaces | `Predicate`, `Function` |
| **Optional** | Null-safe container | `Optional.of(x)` |
| **Method references** | Shorthand for lambdas | `String::toUpperCase` |
| **Default methods** | Methods with body in interfaces | `default void log(){}` |
| **CompletableFuture** | Async programming | `supplyAsync(...)` |
| **New Date/Time API** | Immutable dates | `LocalDate.now()` |

**How to explain**: "The ones I use most are Streams and lambdas for data processing, Optional for null safety, and CompletableFuture for async calls."

---

# 11. @Transactional — Why we need it + Propagation

**Why we need it**: Ensures a group of DB operations either ALL succeed or ALL roll back (ACID atomicity). Without it, a failure midway leaves inconsistent data.

**Example**:
```java
@Transactional
public void transferMoney(Long fromId, Long toId, double amount) {
    accountRepo.debit(fromId, amount);    // step 1
    accountRepo.credit(toId, amount);     // step 2
    // If step 2 throws, step 1 is ROLLED BACK automatically
}
```

**Transaction Propagation** — how a transactional method behaves when called by another transactional method:

| Propagation | Behavior |
|-------------|----------|
| **REQUIRED** (default) | Join existing transaction, or create new if none |
| **REQUIRES_NEW** | Always create a new transaction (suspend the current) |
| **SUPPORTS** | Use transaction if one exists, else run without |
| **NOT_SUPPORTED** | Run without transaction (suspend existing) |
| **MANDATORY** | Must have an existing transaction, else error |
| **NEVER** | Must NOT have a transaction, else error |
| **NESTED** | Nested transaction with savepoint |

**Example (REQUIRES_NEW — audit log must persist even if main fails)**:
```java
@Transactional
public void placeOrder(Order o) {
    orderRepo.save(o);
    auditLog();   // this runs in its OWN transaction
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void auditLog() {
    auditRepo.save(new Audit("ORDER_PLACED"));
    // Even if placeOrder rolls back, this audit stays committed
}
```

**How to explain**: "@Transactional guarantees atomicity. Propagation controls how nested transactional methods interact — REQUIRED joins the caller's transaction, REQUIRES_NEW starts an independent one."

---

# 12. Java Design Patterns

| Category | Pattern | Real Use |
|----------|---------|----------|
| **Creational** | Singleton | Spring beans (default) |
| | Factory | `BeanFactory`, object creation |
| | Builder | `ChatClient.builder()`, `StringBuilder` |
| **Structural** | Adapter | Wrapping incompatible interfaces |
| | Proxy | Spring AOP, `@Transactional` |
| | Decorator | `BufferedReader(new FileReader())` |
| **Behavioral** | Strategy | `VectorStore` interface (swap implementations) |
| | Observer | Event listeners, Kafka consumers |
| | Template Method | `JdbcTemplate`, `RedisTemplate` |

**Example (Builder — in my project)**:
```java
ChatClient client = ChatClient.builder(chatModel).build();
```

**Example (Strategy — in my project)**:
```java
// VectorStore is an interface. I can swap PgVectorStore for another
// implementation without changing my service code.
private final VectorStore vectorStore;
```

---

# 13. JPA and Hibernate

**JPA** = Java Persistence API — a **specification** (interface/rules) for ORM (mapping Java objects to DB tables).

**Hibernate** = the most popular **implementation** of JPA.

**Analogy**: JPA is the interface, Hibernate is the concrete class.

**Example**:
```java
@Entity                          // JPA — maps to a table
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne                   // relationship mapping
    @JoinColumn(name = "dept_id")
    private Department department;
}

// Repository — Spring Data JPA generates the SQL
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByName(String name);   // auto-implemented!
}
```

**How to explain**: "JPA is the standard, Hibernate implements it. I annotate entities with `@Entity`, `@Id`, `@Column`, and Spring Data JPA auto-generates CRUD queries from method names like `findByName`."

---

# 14. Docker — Explain + How I used it in my project

**What is Docker**: A containerization platform. It packages an app + all its dependencies into a **container** that runs identically anywhere — "build once, run anywhere." No more "works on my machine."

**Key concepts**:
| Term | Meaning |
|------|---------|
| **Image** | A blueprint (app + dependencies + OS libs) |
| **Container** | A running instance of an image |
| **Dockerfile** | Recipe to build an image |
| **Docker Compose** | Run multiple containers together |
| **Volume** | Persistent storage |
| **Network** | Containers talk to each other |

**How I used it in my project**:
- Each service (frontend, backend, audit, DB, Redis, Ollama, Kafka) runs in its own container
- **Multi-stage Dockerfile** for the backend (build with JDK, run with smaller JRE)
- **Docker Compose** orchestrates all 8 containers with one command
- Containers talk via Docker DNS (service names like `db`, `redis`)

**Example — my backend Dockerfile (multi-stage)**:
```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

# Stage 2: Run (smaller image)
FROM eclipse-temurin:21-jre
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Example — docker-compose (snippet)**:
```yaml
services:
  backend:
    build: ./backend
    ports: ["8000:8080"]
    depends_on:
      db: { condition: service_healthy }
  db:
    image: pgvector/pgvector:pg16
```

**How to explain**: "I containerized every service. Docker Compose spins up the whole stack — backend, DB, Redis, Ollama, Kafka — with `docker compose up`. Multi-stage builds keep my final image small."

---

# 15. Deployment Process

**My deployment flow**:
```
1. Code committed & pushed to GitHub
2. Terraform provisions GCP infrastructure (VM, IP, firewall)
3. On the VM: git pull latest code
4. docker compose up --build -d  → builds & starts all containers
5. Health checks verify DB, Redis, Ollama are ready
6. Firewall rules expose ports (80/3000/8000)
7. App live at http://VM-IP
```

**For zero-downtime (Kubernetes)**:
```
1. Build Docker image → push to registry
2. kubectl apply → rolling update
3. New pods start, readiness probe checks health
4. Traffic shifts to new pods, old pods terminate
5. If health fails → kubectl rollout undo (auto-rollback)
```

**How to explain**: "I use Terraform for infrastructure-as-code, Docker Compose for orchestration. For production I'd use Kubernetes rolling updates for zero-downtime, with readiness probes and automatic rollback on failure."

---

# 16. How do you solve circular dependency?

**The problem**:
```java
@Service
public class AService {
    @Autowired
    private BService bService;   // A needs B
}

@Service
public class BService {
    @Autowired
    private AService aService;   // B needs A  → CIRCULAR!
}
```

At startup Spring tries to create A → needs B → creates B → needs A → **deadlock**. With constructor injection you get `BeanCurrentlyInCreationException`.

**Solutions (best to worst):**

### Solution 1 (BEST): Refactor — remove the cycle
The cycle usually means bad design. Extract the shared logic into a third service C that both A and B depend on.
```java
@Service
public class CService { /* shared logic */ }

@Service
public class AService {
    private final CService cService;   // A → C
    public AService(CService c) { this.cService = c; }
}

@Service
public class BService {
    private final CService cService;   // B → C  (no cycle!)
    public BService(CService c) { this.cService = c; }
}
```

### Solution 2: @Lazy on one dependency
Delays creating one bean until it's first used, breaking the startup cycle.
```java
@Service
public class AService {
    private final BService bService;
    public AService(@Lazy BService bService) {   // @Lazy breaks the cycle
        this.bService = bService;
    }
}
```

### Solution 3: Setter/Field injection instead of constructor
Field injection lets Spring create both beans first, then inject afterwards.
```java
@Service
public class AService {
    @Autowired
    private BService bService;   // field injection tolerates cycle
}
```

### Solution 4: @PostConstruct wiring (manual)
```java
@Service
public class AService {
    @Autowired private BService bService;
    @PostConstruct
    public void init() { bService.setAService(this); }
}
```

**How to explain**: "A circular dependency usually signals a design smell. My first choice is to refactor — extract shared logic into a third bean. If I truly can't, I use `@Lazy` on one constructor parameter to break the startup cycle. Field injection also works but I avoid it because it hides dependencies and hurts testability."

---

# 17. Create a Many-to-Many mapping between Class A and Class B

**Scenario**: e.g., `Student` (A) and `Course` (B) — a student takes many courses, a course has many students.

```java
@Entity
@Table(name = "class_a")
public class A {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String name;

    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
        name = "a_b_mapping",                          // the join (link) table
        joinColumns = @JoinColumn(name = "a_id"),       // FK to A
        inverseJoinColumns = @JoinColumn(name = "b_id") // FK to B
    )
    private Set<B> bList = new HashSet<>();

    // getters / setters
}

@Entity
@Table(name = "class_b")
public class B {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String name;

    @ManyToMany(mappedBy = "bList")   // "bList" = field name in A (inverse side)
    private Set<A> aList = new HashSet<>();

    // getters / setters
}
```

**What happens in the DB** — three tables:
```
class_a         class_b         a_b_mapping (join table)
+----+------+   +----+------+   +------+------+
| id | name |   | id | name |   | a_id | b_id |
+----+------+   +----+------+   +------+------+
|  1 | John |   |  1 | Math |   |   1  |   1  |   ← John takes Math
|  2 | Anu  |   |  2 | Java |   |   1  |   2  |   ← John takes Java
+----+------+   +----+------+   |   2  |   1  |   ← Anu takes Math
                                +------+------+
```

**Key points to say**:
- `@ManyToMany` on both sides
- `@JoinTable` defines the link table (on the **owning** side — A here)
- `mappedBy` on the **inverse** side (B) points to the field name in A
- Use `Set` (not List) to avoid duplicate links
- `cascade` controls whether saving A also saves related B

**How to explain**: "Many-to-many needs a join table. One side is the owner and declares `@JoinTable`; the other side uses `mappedBy` to point back. I use `Set` to prevent duplicate associations."

---

# 18. Sum valid numeric values from a String array (ignore invalid) — Lambda + Streams

**Problem**:
```java
String[] arr = {"a-20", "b-xyz", "c-40", "d-", "e-50"};
// Each element is "letter-value". Extract the part after "-",
// sum only the valid numbers (20 + 40 + 50 = 110), skip "xyz" and empty.
```

**Solution**:
```java
import java.util.Arrays;

public class SumValidNumbers {
    public static void main(String[] args) {
        String[] arr = {"a-20", "b-xyz", "c-40", "d-", "e-50"};

        int sum = Arrays.stream(arr)                     // Stream<String>
            .map(s -> s.split("-", 2))                   // ["a","20"], ["b","xyz"]...
            .filter(parts -> parts.length == 2)          // must have a value part
            .map(parts -> parts[1])                      // take the value: "20","xyz",...
            .filter(SumValidNumbers::isNumeric)          // keep only valid numbers
            .mapToInt(Integer::parseInt)                 // String -> int
            .sum();                                      // add them up

        System.out.println("Sum = " + sum);   // Sum = 110
    }

    // Safe numeric check — no exceptions
    private static boolean isNumeric(String str) {
        if (str == null || str.isBlank()) return false;
        return str.chars().allMatch(Character::isDigit);
    }
}
```

**Alternative — handle safely with try/catch inside a helper returning a Stream**:
```java
int sum = Arrays.stream(arr)
    .map(s -> s.substring(s.indexOf('-') + 1))   // part after '-'
    .mapToInt(SumValidNumbers::parseSafe)        // invalid -> 0
    .sum();

private static int parseSafe(String value) {
    try { return Integer.parseInt(value.trim()); }
    catch (NumberFormatException e) { return 0; }   // ignore invalid
}
```

**Step-by-step trace**:
```
"a-20"  → split → ["a","20"]  → "20"  → numeric? yes → 20
"b-xyz" → split → ["b","xyz"] → "xyz" → numeric? no  → skipped
"c-40"  → split → ["c","40"]  → "40"  → numeric? yes → 40
"d-"    → split → ["d",""]    → ""    → numeric? no  → skipped
"e-50"  → split → ["e","50"]  → "50"  → numeric? yes → 50
                                              Total = 110
```

**How to explain**: "I split each string on the dash, take the value part, filter out non-numeric ones with a safe `isNumeric` check (no exceptions), then map to int and sum. The safe check avoids `NumberFormatException` — that's the 'safely' part."

---

# QUICK REVISION TABLE

| Topic | One-liner |
|-------|-----------|
| Streams | Declarative data processing, lazy until terminal op |
| Optional | Null-safe container, use map/orElse |
| Factory | Centralize object creation, hide concrete class |
| Data security | Secret Manager + TLS + JWT + BCrypt + validation |
| Circuit Breaker | Stop calling failed service, return fallback |
| Bean Scope | singleton (default), prototype, request, session |
| 2 Databases | 2 DataSources + EntityManagers + TxManagers, @Primary |
| @Transactional | Atomicity — all or nothing; propagation controls nesting |
| JPA vs Hibernate | JPA = spec, Hibernate = implementation |
| Docker | Package app+deps into portable containers |
| Deployment | Terraform + Docker Compose; K8s rolling update for prod |
| Circular dependency | Refactor to 3rd bean; or @Lazy; or field injection |
| Many-to-Many | @ManyToMany + @JoinTable (owner) + mappedBy (inverse) |
| Safe sum from strings | split → filter numeric → mapToInt → sum |

---

*Good luck!*
