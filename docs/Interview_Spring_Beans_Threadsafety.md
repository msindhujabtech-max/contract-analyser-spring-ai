# Spring Beans, Immutability & Thread Safety — Deep Q&A
## (The exact questions asked in the interview, answered clearly)

These questions are all connected — they test whether you understand how Spring beans behave in a multi-threaded web server. Read them in order; each builds on the previous.

---

## The Big Picture First (understand this and all answers become easy)

In Spring Boot:
- Beans are **singletons by default** → ONE object shared by ALL requests/threads.
- A web server handles **many requests concurrently** (many threads hit the same bean).
- So the key question is always: **"Does this shared bean hold mutable state that threads can corrupt?"**

The golden rule:
> **A singleton bean is thread-safe IF it is stateless (or its state is immutable).**

---

## Q1. Is the injected component (dependency) mutable or immutable?

**Short answer**: The *reference* should be immutable (use `final`), but the object it points to is a normal Java object — mutable unless designed otherwise.

**Explanation**:
When you inject a dependency via constructor and mark it `final`, the **reference cannot be reassigned** — that makes it effectively immutable *as a reference*.

```java
@Service
public class ChatController {
    private final RagService ragService;   // final = reference can't change

    public ChatController(RagService ragService) {
        this.ragService = ragService;      // set once, never reassigned
    }
}
```

**What to say in interview**:
> "The injected dependency reference should be immutable — I declare it `final` and inject via constructor, so it's assigned once and never changes. This is the recommended, thread-safe way. The dependency object itself is a singleton service that I design to be stateless."

---

## Q2. Configuration file structure — where are component/class files stored in Spring Boot?

**Answer**: Spring Boot follows a standard Maven/Gradle project structure:

```
src/
 └── main/
      ├── java/
      │    └── com/contract/analyser/           ← base package (root)
      │         ├── ContractAnalyserApplication.java   ← @SpringBootApplication (entry)
      │         ├── config/                     ← @Configuration classes (beans)
      │         │    ├── AiConfig.java
      │         │    ├── RedisConfig.java
      │         │    └── CorsConfig.java
      │         ├── controller/                 ← @RestController classes
      │         │    ├── ChatController.java
      │         │    └── UploadController.java
      │         ├── service/                    ← @Service classes (business logic)
      │         │    ├── RagService.java
      │         │    └── CacheService.java
      │         ├── repository/                 ← @Repository (data access)
      │         └── dto/                        ← DTOs / records
      │              └── ChatRequest.java
      └── resources/
           ├── application.yml                  ← main config file
           ├── application-gcp.yml              ← profile-specific config
           └── static / templates               ← web resources
```

**Key rules to state**:
- `@SpringBootApplication` sits in the **root package** (`com.contract.analyser`).
- `@ComponentScan` (inside `@SpringBootApplication`) scans that package + **all sub-packages** automatically.
- Configuration properties go in `src/main/resources/application.yml` (or `.properties`).
- Profile-specific configs: `application-{profile}.yml` (e.g., `application-gcp.yml`).

**What to say**:
> "The main class with `@SpringBootApplication` lives in the root package. Spring automatically scans that package and all sub-packages for `@Component`, `@Service`, `@Controller`, `@Configuration`. Config values live in `application.yml` under `src/main/resources`. In my project I organize by layer — config, controller, service, dto packages."

---

## Q3. Which collection would you prefer for storing objects? Why?

**Answer**: "It depends on the requirement" — then give the decision logic:

| Need | Collection | Why |
|------|-----------|-----|
| Indexed access, order, allow duplicates | **ArrayList** | O(1) random access, most common |
| No duplicates, fast lookup | **HashSet** | O(1) contains/add, no order |
| No duplicates + insertion order | **LinkedHashSet** | O(1) + keeps order |
| No duplicates + sorted | **TreeSet** | O(log n), sorted |
| Key-value lookup | **HashMap** | O(1) get/put |
| Key-value + insertion order | **LinkedHashMap** | O(1) + order |
| Frequent insert/delete in middle | **LinkedList** | O(1) insert/delete |
| **Thread-safe** map | **ConcurrentHashMap** | Safe for concurrent access |
| **Thread-safe** list | **CopyOnWriteArrayList** | Safe for read-heavy concurrent |

**What to say**:
> "It depends on the use case. For general storage with indexed access I use `ArrayList`. If I need uniqueness I use `HashSet`, and `LinkedHashSet` if I also need insertion order. For key-value I use `HashMap`. In a multi-threaded scenario I'd switch to `ConcurrentHashMap` or `CopyOnWriteArrayList` for thread safety."

**Follow-up trap**: If they say "in a multi-threaded app?" → answer **ConcurrentHashMap** (not `Collections.synchronizedMap`, because ConcurrentHashMap locks only segments, giving better concurrency).

---

## Q4. Is a singleton-scoped component mutable or immutable?

**Answer**: By default a singleton bean is a **normal Java object — it CAN be mutable**. Spring doesn't make it immutable for you.

BUT — **best practice is to keep singleton beans stateless (effectively immutable)** so they're thread-safe.

**Example**:
```java
// ❌ BAD — mutable singleton (NOT thread-safe)
@Service
public class CounterService {
    private int count = 0;           // shared mutable state!
    public void increment() { count++; }   // two threads corrupt this
}

// ✅ GOOD — stateless singleton (thread-safe)
@Service
public class RagService {
    private final VectorStore vectorStore;   // only final dependencies
    // no mutable instance fields → nothing to corrupt
}
```

**What to say**:
> "A singleton bean is a plain object — it can be mutable. Spring doesn't enforce immutability. But since it's shared across all threads, best practice is to keep it stateless — only `final` injected dependencies, no mutable instance fields. That makes it effectively immutable and thread-safe."

---

## Q5. Is a singleton-scoped component thread-safe?

**Answer**: **NOT automatically.** A singleton is shared by all threads, so it's thread-safe ONLY IF it's stateless or its state is immutable.

**The reasoning**:
- Singleton = one instance = shared by all concurrent requests/threads
- If it has **mutable instance fields** → race conditions → NOT thread-safe
- If it's **stateless** (no mutable fields) → thread-safe

**Example**:
```java
// NOT thread-safe — mutable field shared across threads
@Service
public class OrderService {
    private Order currentOrder;   // ❌ thread A and B overwrite each other
    public void process(Order o) { this.currentOrder = o; ... }
}

// Thread-safe — no shared mutable state
@Service
public class OrderService {
    private final OrderRepository repo;   // ✅ final dependency only
    public void process(Order o) {         // 'o' is a local variable, per-thread
        repo.save(o);
    }
}
```

**Key insight**: **Local variables and method parameters are thread-safe** (each thread has its own stack). Only **instance fields** are shared and risky.

**What to say**:
> "No, a singleton is not automatically thread-safe. Since one instance serves all threads, it's thread-safe only if it has no mutable shared state. I keep my services stateless — only final dependencies, and all working data stays in local variables and method parameters, which are per-thread on the stack."

---

## Q6. How can we inject components? (Types of DI)

**Answer**: Three ways:

### 1. Constructor Injection (RECOMMENDED)
```java
@Service
public class ChatController {
    private final RagService ragService;
    public ChatController(RagService ragService) {   // injected via constructor
        this.ragService = ragService;
    }
}
```

### 2. Setter Injection
```java
@Service
public class ChatController {
    private RagService ragService;
    @Autowired
    public void setRagService(RagService ragService) {
        this.ragService = ragService;
    }
}
```

### 3. Field Injection (NOT recommended)
```java
@Service
public class ChatController {
    @Autowired
    private RagService ragService;   // injected directly into field
}
```

**What to say**:
> "Three ways: constructor, setter, and field injection. I prefer constructor injection because it allows `final` fields, makes dependencies explicit and mandatory, and is easier to unit test."

---

## Q7. @Autowired vs Constructor injection — which is thread-safe?

**Answer**: **Constructor injection is more thread-safe** because it allows `final` fields.

**The reasoning**:

| | Constructor Injection | Field Injection (@Autowired) |
|--|----------------------|------------------------------|
| Can use `final`? | ✅ YES | ❌ NO (field is set after construction) |
| Fully initialized before use? | ✅ YES (before object exists) | ❌ Object exists first, then injected |
| Thread-safe? | ✅ More — final = safely published | ⚠️ Less — non-final, mutable reference |
| Testable? | ✅ Easy (pass mocks) | ❌ Needs reflection |

**Why constructor + final is thread-safe**:
- `final` fields are guaranteed **safely published** by the JVM Memory Model — once the constructor finishes, all threads see the fully-initialized value. No partial construction visible.
- Field injection sets the field *after* the object is created, so there's a window where the reference is non-final and mutable.

**What to say**:
> "Constructor injection is more thread-safe because it lets me declare the field `final`. Final fields are safely published by the Java Memory Model — once the constructor completes, every thread sees the fully-initialized dependency. Field injection with @Autowired can't be final, so the reference is technically mutable and not guaranteed to be safely published."

---

## Q8. Can we use `final` access modifier with @Autowired?

**Answer**: **Not with field injection.** `final` fields must be assigned in the constructor, but `@Autowired` field injection happens AFTER the object is constructed — so a `final` field wouldn't be assigned in time → **compile error**.

```java
// ❌ DOES NOT COMPILE
@Service
public class ChatController {
    @Autowired
    private final RagService ragService;   // ERROR: final field not initialized
}

// ✅ CORRECT — final works with CONSTRUCTOR injection
@Service
public class ChatController {
    private final RagService ragService;   // final OK here
    public ChatController(RagService ragService) {
        this.ragService = ragService;      // assigned in constructor
    }
}
```

**Note**: `@Autowired` on a *constructor* works fine with final (and since Spring 4.3, `@Autowired` is optional if there's only one constructor).

**What to say**:
> "You can't use `final` with `@Autowired` field injection because final fields must be assigned during construction, but field injection happens after the object is built. `final` works only with constructor injection — which is exactly why constructor injection is preferred."

---

## Q9. How do you make sure a class is thread-safe?

**Answer** — multiple techniques (mention several):

### 1. Make it stateless (BEST for Spring beans)
No mutable instance fields → nothing to corrupt.
```java
@Service
public class RagService {
    private final VectorStore vectorStore;   // only final deps, no mutable state
}
```

### 2. Immutability
Make fields `final`, don't provide setters. Use records for data.
```java
public record ChatRequest(Long contractId, Long userId, String question) {}
// Immutable — can't be changed after creation → inherently thread-safe
```

### 3. Synchronization
Guard critical sections so only one thread enters at a time.
```java
public synchronized void increment() { count++; }
// or
synchronized (lock) { /* critical section */ }
```

### 4. Concurrent collections
```java
private final Map<String, Object> cache = new ConcurrentHashMap<>();
```

### 5. Atomic variables
```java
private final AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();   // atomic, no locks needed
```

### 6. Locks (fine-grained control)
```java
private final ReentrantLock lock = new ReentrantLock();
lock.lock();
try { /* critical section */ } finally { lock.unlock(); }
```

### 7. ThreadLocal (per-thread copy)
```java
private static final ThreadLocal<SimpleDateFormat> formatter =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
```

**What to say**:
> "The simplest way for a Spring bean is to keep it stateless — no mutable instance fields, only final dependencies, and keep working data in local variables. If I do need shared state, I use immutable objects, `ConcurrentHashMap`, atomic variables like `AtomicInteger`, or `synchronized`/`ReentrantLock` for critical sections. In my project all my services are stateless singletons, so they're naturally thread-safe."

---

## THE ONE-PARAGRAPH ANSWER THAT TIES IT ALL TOGETHER

If an interviewer asks the theme behind all these questions, say:

> "Spring beans are singletons by default — one instance shared across all request threads. So thread safety comes down to state: a stateless bean is inherently thread-safe. I achieve this with constructor injection and `final` dependencies (safely published by the JVM), no mutable instance fields, and keeping all working data in local variables and method parameters which live on each thread's own stack. When I genuinely need shared mutable state, I reach for immutable objects, concurrent collections, or atomic variables rather than manual synchronization."

---

## QUICK REVISION TABLE

| Question | Answer |
|----------|--------|
| Injected component mutable/immutable? | Reference should be immutable (`final`); object is stateless by design |
| Where are files stored? | `src/main/java/<root-package>/{config,controller,service,dto}`, config in `resources/application.yml` |
| Which collection? | Depends — ArrayList (general), HashSet (unique), HashMap (key-value), ConcurrentHashMap (thread-safe) |
| Singleton mutable/immutable? | Can be mutable, but best practice = stateless (effectively immutable) |
| Singleton thread-safe? | NOT automatically — only if stateless/immutable |
| How to inject? | Constructor (best), setter, field |
| @Autowired vs Constructor — thread-safe? | Constructor — allows `final`, safely published |
| Can final be used with @Autowired? | Not with field injection; only with constructor injection |
| How to make class thread-safe? | Stateless, immutability, ConcurrentHashMap, atomics, synchronized/locks |

---

*Master this file — these questions come up constantly for experienced Java roles.*

---

# MORE SIMILAR "DEPTH" QUESTIONS (same style — know these too)

These follow the same pattern: they test whether you truly understand *why*, not just definitions.

---

## Q10. Default scope of a Spring bean? Other scopes?

**Answer**: Default is **singleton** (one instance per container). Others: **prototype** (new each request), **request**, **session**, **application**, **websocket**.

**Trap**: "Prototype bean injected into a singleton — how many instances?" → **Only ONE** (injected once at singleton creation). For a fresh one each time use `ObjectFactory`, `Provider`, or `@Lookup`.

---

## Q11. @Bean vs @Component (deeper)

| @Component | @Bean |
|-----------|-------|
| Class-level | Method-level (in @Configuration) |
| Auto-detected by scanning | You define explicitly |
| For your classes | For third-party classes |
| One bean per class | Multiple beans of same type possible |

---

## Q12. Two beans of the same type — how does Spring resolve it?

**Answer**: Throws `NoUniqueBeanDefinitionException`. Fix with `@Primary` (default choice) or `@Qualifier("name")` (explicit pick).

```java
@Bean @Primary
public DataSource primaryDs() { ... }
@Bean
public DataSource backupDs() { ... }

public Service(@Qualifier("backupDs") DataSource ds) { ... }
```

---

## Q13. @Controller vs @RestController

`@RestController` = `@Controller` + `@ResponseBody`. Controller returns a **view name** (HTML); RestController returns **data** (JSON) in the body.

---

## Q14. @RequestParam vs @PathVariable

```java
@GetMapping("/users/{id}")   // GET /users/5
public User get(@PathVariable Long id) { }

@GetMapping("/users")        // GET /users?id=5
public User get(@RequestParam Long id) { }
```

---

## Q15. How does Spring Boot auto-configuration work?

**Answer**: `@EnableAutoConfiguration` reads `AutoConfiguration.imports` from each starter jar. Each config uses **conditions** (`@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`) to decide whether to activate. Add a Redis starter → Spring auto-creates a `RedisConnectionFactory` unless you defined your own.

---

## Q16. What is @SpringBootApplication made of?

`@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.

---

## Q17. application.properties vs application.yml — which wins?

Same purpose, different syntax. If both exist, `.properties` wins for overlapping keys. Resolution order: command-line > env vars > application-{profile} > application.yml > code defaults.

---

## Q18. Bean lifecycle

```
1. Instantiate (constructor)
2. Inject dependencies
3. @PostConstruct (init)
4. Ready & used
5. @PreDestroy (cleanup)
6. Destroyed
```

---

## Q19. @Transactional at class vs method level

Class-level applies to all public methods; method-level overrides for that method.

**Trap**: "Does @Transactional work on a private method?" → **No.** It's proxy-based — only intercepts public methods called from outside the class.

---

## Q20. Why doesn't @Transactional work on a self-invoked method?

**Answer**: Spring uses proxy-based AOP. Calling `this.method()` hits the real object directly, bypassing the proxy — so transaction advice never runs. Fix: move it to another bean, or self-inject the proxy.

---

## Q21. Checked vs unchecked exceptions — which triggers rollback?

**Answer**: By default `@Transactional` rolls back only on **unchecked (RuntimeException)** and Errors — NOT checked exceptions. For checked: `@Transactional(rollbackFor = Exception.class)`.

---

## Q22. == vs .equals() vs hashCode()

- `==` compares references (address)
- `.equals()` compares content
- Contract: equal objects MUST have the same hashCode

**Trap**: "Override equals but not hashCode?" → HashMap/HashSet break — equal objects land in different buckets.

---

## Q23. How does HashMap work internally?

Array of buckets. `hashCode()` → bucket index. Collisions stored as linked list, converted to a red-black tree when a bucket exceeds 8 entries (Java 8+). `equals()` finds the exact key in the bucket.

---

## Q24. Fail-fast vs fail-safe iterators

- **Fail-fast** (ArrayList, HashMap): throw `ConcurrentModificationException` if modified during iteration.
- **Fail-safe** (CopyOnWriteArrayList, ConcurrentHashMap): iterate a snapshot, no exception.

---

## Q25. Comparable vs Comparator

```java
// Comparable — natural order, one way, in the class
class Employee implements Comparable<Employee> {
    public int compareTo(Employee o) { return this.id - o.id; }
}
// Comparator — external, multiple orderings
Comparator<Employee> bySalary = Comparator.comparingDouble(Employee::salary);
```

---

## Q26. synchronized vs ReentrantLock

| synchronized | ReentrantLock |
|-------------|---------------|
| Keyword, auto release | Manual lock()/unlock() |
| No try/timeout | tryLock(), timed |
| Not interruptible | Interruptible |
| Simpler | More flexible |

---

## Q27. volatile vs synchronized

- `volatile` — guarantees **visibility** only (all threads see latest value), NOT atomicity.
- `synchronized` — guarantees **visibility AND atomicity**.

**Example**: `volatile boolean running` (flag) is fine, but `volatile int count; count++` is NOT safe — use `AtomicInteger`.

---

## Q28. Runnable vs Callable

```java
Runnable → run()  → void, no checked exceptions
Callable → call() → returns value, can throw checked exceptions
```
Use Callable with ExecutorService when you need a result (`Future<T>`).

---

## Q29. Thread states

`NEW → RUNNABLE → BLOCKED / WAITING / TIMED_WAITING → TERMINATED`

---

## Q30. Can a memory leak happen with garbage collection?

**Answer**: Yes. GC only removes *unreachable* objects. Unintended references (static collections, unclosed resources, uncleared ThreadLocal) stay reachable → never collected → leak.

---

## RAPID-FIRE CONCEPTUAL TRAPS

| Question | Answer |
|----------|--------|
| Default bean scope? | Singleton |
| Field injection recommended? | No — use constructor |
| @Transactional on private method? | No (proxy only sees public) |
| @Transactional rolls back on checked exception? | No, only unchecked (unless rollbackFor) |
| Prototype in singleton — instances? | One (injected once) |
| Two beans same type — resolve? | @Primary or @Qualifier |
| String immutable? | Yes |
| StringBuilder vs StringBuffer? | Builder faster; Buffer thread-safe |
| HashMap thread-safe? | No — ConcurrentHashMap |
| volatile gives atomicity? | No — only visibility |
| final field thread-safe? | Yes — safely published |
| equals without hashCode? | Breaks HashMap/HashSet |

---

## HOW TO HANDLE ANY "DEPTH" QUESTION

1. **State the direct answer** (yes/no + one line)
2. **Give the reason** (the *why*)
3. **Give a small example or your project reference**
4. **Mention the trap/edge case** to show depth

Example: "Is a singleton thread-safe? → No, not automatically. Because one instance is shared by all threads, mutable fields cause races. In my project I keep services stateless with final dependencies. The exception is if state is immutable — then it's safe."

---

*These "depth" questions separate senior candidates. Practice explaining the WHY out loud.*
