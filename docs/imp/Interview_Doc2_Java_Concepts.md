# Doc 2 — Core Java Concepts (explained in depth via BDSI)

Each concept is explained the way you'd say it in an interview: **what it is → why it matters → how it works → the real BDSI example → the gotcha an interviewer will probe.** Read the prose, not just the code.

---

## 1. OOP pillars

### 1.1 Inheritance
**What it is:** A class (child) reuses and extends the fields/methods of another class (parent). It models an "is-a" relationship.

**How BDSI uses it:** The reporting classes form a chain:
```
UploadedReplenishmentExcelReport  extends  ExcelBulkLoadService
ExcelBulkLoadService              extends  GenericBulkLoadService
GenericBulkLoadService (abstract) extends  GenericService  (ATG platform base)
```
`GenericService` (from ATG) provides lifecycle hooks (`doStartService()`, `doStopService()`) and logging (`vlogDebug`, `vlogError`). `ExcelBulkLoadService` adds Excel/CSV helpers. `UploadedReplenishmentExcelReport` adds the specific report layout. Each level only adds what's unique to it, so nothing is duplicated.

**Why it matters:** Reuse and a clear hierarchy. When I built the replenishment report I got logging, lifecycle, and bulk-load helpers "for free" from the parents and only wrote the report-specific code.

**Interview gotcha:** *"Isn't deep inheritance bad?"* — Yes, favor composition over deep hierarchies. Here the chain is shallow and each layer has a clear responsibility, which is acceptable. Overuse of inheritance leads to the fragile-base-class problem.

### 1.2 Abstraction
**What it is:** Hiding "how" behind a defined "what." An abstract class can't be instantiated and can declare methods subclasses must implement.

**BDSI example:**
```java
public abstract class GenericBulkLoadService extends GenericService {
    public abstract Object getTemplate();   // every bulk-load service must define its template
}
```
Callers work with the abstraction (a bulk-load service) without caring which concrete report it is.

**Why it matters:** It lets the framework call `getTemplate()` on any bulk-load service uniformly while each report supplies its own template.

### 1.3 Interfaces & polymorphism (the SAP messaging design)
**What it is:** An interface is a pure contract (methods, no state). Polymorphism means one method call behaves differently depending on the actual object type at runtime.

**BDSI example — this is the cleanest polymorphism story in the codebase:**
```java
public interface WMQMessageSink {
    void receiveMessage(BDSIWmqJmsMessage messageObj) throws JMSException, MessageSinkException;
}
class OrderConfirmedStatusMessageSink implements WMQMessageSink { /* confirm logic */ }
class OrderShippedStatusMessageSink   implements WMQMessageSink { /* shipment logic */ }
class OrderInvoiceMessageSink         implements WMQMessageSink { /* invoice logic */ }
```
The dispatcher (`OrderStatusMessageManager`) keeps a map of message-type → sink and does:
```java
getMessageTypeSinkMap().get(msg.getMessageType()).receiveMessage(msg);
```
It never uses `if/else` on the message type. The **runtime object** decides whether confirmation, shipment, or invoice logic runs.

**Why it matters:** Adding a new SAP message type = write a new sink + register it in config; the dispatcher code never changes. This is the Open/Closed Principle in action.

**Interview gotcha:** *"Interface vs abstract class?"* — Interface = a contract with no state (a class can implement many); use when unrelated classes share a capability (many sinks, many `FeedProcessor`s). Abstract class = shared state + partial implementation (single inheritance); use when subclasses are variations of one base (the bulk-load reports).

### 1.4 Encapsulation
**What it is:** Keep fields private, expose controlled access via methods. Internal representation can change without breaking callers.

**BDSI example:** `@Getter @Setter private Map<String,String> transactionDataMap;` — callers set/read through methods (Lombok-generated), never touch the field directly. ATG's component model relies on this: it calls setters to inject configuration.

---

## 2. Lombok (why the code has so few getters/setters)
**What it is:** A library that generates boilerplate (getters, setters, constructors, `toString`, `equals/hashCode`) at compile time from annotations.

**BDSI example:**
```java
@Data @EqualsAndHashCode(callSuper=false)     // full data class
public class ImmASNReportScheduler extends SingletonSchedulableService { ... }

@Getter @Setter private boolean enabled;      // isEnabled()/setEnabled()
```
`@Data` bundles `@Getter @Setter @ToString @EqualsAndHashCode @RequiredArgsConstructor`.

**How it works:** Lombok is an annotation processor that injects the methods into the compiled bytecode — the source stays clean.

**Why it matters:** ATG components are essentially JavaBeans (config injected via setters). Lombok removes dozens of trivial accessor methods per class.

**Interview gotcha:** Be careful using `@Data` on JPA entities — the generated `equals/hashCode` uses all fields, which breaks with lazy-loaded/mutable fields and identity semantics. For plain VOs like `UploadedReplenishmentOrderVO` it's fine.

---

## 3. Collections — choosing the right one
**What it is:** The standard data-structure library (`List`, `Set`, `Map` and their implementations).

**BDSI examples and the reasoning:**
```java
List<UploadedReplenishmentOrderVO> orders = new ArrayList<>();   // ordered + fast index access
Map<String,String> txnMap = new LinkedHashMap<>();               // MUST keep insertion order
Set<String> orgIds = new HashSet<>(contactCustomerMap.values()); // want unique org ids
Set<X> retry = Collections.newSetFromMap(new ConcurrentHashMap<>()); // thread-safe set for parallel processing
```
- **`ArrayList`** — backed by an array; O(1) index access, cheap iteration. Used for report rows.
- **`LinkedHashMap`** — a `HashMap` that remembers insertion order. Critical for `transactionDataMap` because Excel columns must appear 1→13 in order; a plain `HashMap` would scramble them.
- **`HashSet`** — deduplicates org ids; O(1) contains.
- **`ConcurrentHashMap`-backed set** — in `OrderStatusMessageManager`, messages are processed in parallel streams, so the "retry" set must be safe for concurrent writes.

**Why it matters:** The wrong choice causes subtle bugs (scrambled columns) or race conditions (lost retries). Interviewers love "why LinkedHashMap here?"

**Interview gotcha:** `HashMap` vs `LinkedHashMap` vs `TreeMap` — no order / insertion order / sorted order. `HashMap` is not thread-safe; `ConcurrentHashMap` is (and doesn't lock the whole map, unlike `Collections.synchronizedMap`).

---

## 4. Generics
**What it is:** Parameterized types that give compile-time type safety and remove casts.

**BDSI examples:**
```java
ServiceMap<WMQMessageSink> messageTypeSinkMap;              // a typed map of sinks
Map<String, List<UploadedReplenishmentOrderVO>> byCustomer; // nested generics
List<? extends BaseFeedProcessVO> voList;                   // bounded wildcard (feed module)
```
**How it works:** Generics are erased at runtime (type erasure) — they exist mainly for the compiler. `List<String>` and `List<Integer>` are the same class at runtime.

**Why it matters:** `messageTypeSinkMap.get(type)` returns a `WMQMessageSink` with no cast; the compiler guarantees you can't put the wrong type in.

**Interview gotcha:** *"What is type erasure?"* — generic type info is removed after compilation; that's why you can't do `new T()` or `instanceof List<String>`. Bounded wildcards (`? extends`, `? super`) — PECS: Producer Extends, Consumer Super.

---

## 5. Java 8 Streams & lambdas (used across every scheduler)
**What it is:** A declarative pipeline for processing collections (filter/map/reduce) using functional-style lambdas.

**BDSI examples with explanation of each stage:**
```java
// Group all pending replenishment transactions by their customer number.
Map<String,List<UploadedReplenishmentOrderVO>> byCustomer = txns.stream()
    .collect(Collectors.groupingBy(UploadedReplenishmentOrderVO::getCustomerNumber));
```
`stream()` opens the pipeline; `groupingBy` is a collector that builds a `Map<customer, List<txns>>`. This is exactly the SH-7690 requirement "group by customer for one email per customer."

```java
// Count orders that are NOT hard-stop exceptions (the "Total Fillup Orders" number).
int totalOrders = (int) orders.stream()
    .filter(o -> !o.isHardStopException())   // lambda predicate
    .count();                                // terminal operation
```

```java
// Find the first non-blank submitter email; null if none.
String email = orders.stream()
    .map(UploadedReplenishmentOrderVO::getOrderedBy)  // method reference = o -> o.getOrderedBy()
    .filter(StringUtils::isNotBlank)
    .findFirst()
    .orElse(null);                                    // Optional unwrap
```

**Key ideas to say out loud:**
- **Lambda** `o -> !o.isHardStopException()` is an anonymous function passed as data.
- **Method reference** `VO::getOrderedBy` is shorthand for a lambda that calls that method.
- **Lazy evaluation:** intermediate ops (`filter`, `map`) do nothing until a **terminal op** (`count`, `collect`, `findFirst`) runs.
- **Optional** (`findFirst().orElse(null)`) forces you to handle "not found" instead of risking a `NullPointerException`.

**Sorting with null-safety (real line I wrote):**
```java
orderList.sort(Comparator.comparing(
    UploadedReplenishmentOrderVO::getOrderNumber,
    Comparator.nullsLast(String::compareTo)));   // SAP-failed orders have null order#, sort them last
```

**Interview gotcha:** *"When NOT to use streams?"* — tight performance-critical loops, or when the logic is clearer as a plain loop. Also `parallelStream` only helps for large CPU-bound work and can hurt for small/IO-bound tasks.

---

## 6. Exception handling
**What it is:** Structured error handling with `try/catch/finally`, checked vs unchecked exceptions, and custom exception types.

### 6.1 try/catch/finally
```java
XSSFWorkbook workbook = null;
try {
    workbook = report.generateReport(...);   // may throw
    // ... write file, send email ...
} catch (Exception e) {
    vlogError(e, "Exception while processing replenishment notification");  // log, don't crash the scheduler
} finally {
    if (workbook != null) try { workbook.close(); } catch (Exception ignore) {}  // always free POI memory
}
```
The `finally` guarantees the workbook (which holds a lot of memory) is closed even if sending fails.

### 6.2 try-with-resources (the modern way)
```java
try (Connection con = ((GSARepository) repo).getDataSource().getConnection();
     PreparedStatement ps = con.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {
    while (rs.next()) { ... }
}   // con, ps, rs auto-closed in reverse order, even on exception
```
Any resource implementing `AutoCloseable` is closed automatically. This prevents connection/cursor leaks — a classic production bug.

### 6.3 Custom exceptions + recoverable vs fatal (a real design decision)
```java
throw new MessageSinkException(e);   // "recoverable" — dispatcher will retry later
throw new ExternalFileProcessorException(filePath, null, "Invalid file", e);
```
In `OrderStatusMessageManager` the distinction is deliberate:
```java
catch (Exception e) {
    if (e instanceof MessageSinkException) {   // e.g. order not yet present → retry
        addToEmailQueue(msg);  msg.setRemarks(e.getMessage());  return false;  // mark state=2 (retry)
    } else {                                    // truly broken message
        txnRequestRepositoryTools.addFailedStatusMessageForReport(msg);        // mark failed, report it
    }
}
```

**Why it matters:** Not every failure should be retried forever. A missing SAP order might arrive in 2 minutes (retry); a malformed XML never will (fail fast, report). Encoding that in exception types keeps the logic clean.

**Interview gotcha:** Checked (`Exception`, `IOException`) must be declared/handled; unchecked (`RuntimeException`) needn't be. Don't swallow exceptions silently; don't use exceptions for normal control flow.

---

## 7. Multithreading & concurrency (real, in the message manager)
**What it is:** Running work on multiple threads to increase throughput, safely.

**BDSI example — `OrderStatusMessageManager` processes SAP status messages three ways (configurable):**
```java
// 1) sequential
messages.forEach(m -> process(m));

// 2) parallel stream (uses the shared ForkJoin common pool)
messages.parallelStream().forEach(m -> process(m));

// 3) explicit thread pool with CompletableFuture
ExecutorService pool = Executors.newFixedThreadPool(executorThreads);   // executorThreads = 4
List<CompletableFuture<BDSIWmqJmsMessage>> futures = new ArrayList<>();
for (BDSIWmqJmsMessage m : messages)
    futures.add(CompletableFuture.supplyAsync(() -> process(m), pool));
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();  // wait for all
```
The shared "retry" set is thread-safe:
```java
Set<BDSIWmqJmsMessage> retry = Collections.newSetFromMap(new ConcurrentHashMap<>());
```

**Why it matters:** SAP can send thousands of status messages; parallel processing clears the backlog faster. But shared state (the retry set, DB updates) must be thread-safe or you get lost updates / race conditions.

**How to explain each tool:**
- **`ExecutorService`** — a managed pool of reusable threads; you submit tasks, it schedules them. Fixed pool = bounded concurrency (won't spawn unlimited threads).
- **`CompletableFuture`** — represents an async result you can compose (`supplyAsync`, `thenApply`, `allOf`). Cleaner than raw threads/`Future`.
- **`parallelStream`** — easiest parallelism but uses a shared pool and gives less control; good for large CPU-bound work.
- **`ConcurrentHashMap`** — lock-striped map safe for concurrent reads/writes without locking the whole map.

**Interview gotcha:** `SimpleDateFormat` is **not thread-safe** — sharing one instance across parallel tasks corrupts output. This is real here because the report parses dates; create a new formatter per use or use `java.time.DateTimeFormatter` (immutable/thread-safe). Also mention deadlock, race conditions, and why you bound the pool size.

---

## 8. JDBC (raw SQL access)
**What it is:** The low-level Java API to run SQL directly.

**BDSI example (fetching replenishment transactions):**
```java
try (Connection con = ((GSARepository) repo).getDataSource().getConnection();
     PreparedStatement ps = con.prepareStatement(replenishmentTxnSql)) {
    ps.setString(1, customerNumber);            // bind parameter — prevents SQL injection
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            vo.setId(rs.getString("id"));
            vo.setQty(rs.getString("received_qty"));
            Timestamp ts = rs.getTimestamp("order_date");
        }
    }
}
```
**Why it matters:** ATG's repository layer is great for CRUD, but complex reporting joins are far clearer as hand-written SQL. `PreparedStatement` precompiles the query and binds parameters — safe and faster for repeated calls.

**Interview gotcha:** `Statement` vs `PreparedStatement` — the latter parameterizes (no injection, plan caching). Always close in try-with-resources. The feed module uses Spring's `JdbcTemplate`, which removes this boilerplate (`queryForList`, `RowMapper`).

---

## 9. Reflection (dynamic field→column mapping)
**What it is:** Inspecting and accessing classes/fields/methods at runtime rather than compile time.

**BDSI example — the Excel report maps VO fields to columns generically:**
```java
public static List<Field> getAllFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> c = type; c != null; c = c.getSuperclass())   // walk up the class hierarchy
        for (Field f : c.getDeclaredFields()) fields.add(f);
    return fields;
}
// ...
field.setAccessible(true);                       // allow reading private fields
if (field.getName().equalsIgnoreCase(mapValue))  // column config says "put field X here"
    Object value = field.get(orderData);         // read the value dynamically
```
**Why it matters:** The column-to-field mapping lives in **config** (`transactionDataMap`). Reflection lets one generic loop populate any report without hardcoding `getOrderNumber()`, `getQty()`, etc. Add a column in config → no code change.

**Interview gotcha:** Reflection is powerful but (a) slower than direct calls, (b) breaks encapsulation (`setAccessible(true)`), (c) not caught by the compiler (typos fail at runtime). Use it for frameworks/mapping, not everyday logic. SonarQube even flags the `setAccessible` call.

---

## 10. Serialization / marshalling — JAXB & JSON
**What it is:** Converting objects to/from a wire format (XML/JSON).

**BDSI example — the entire SAP integration is XML via JAXB:**
```java
// Unmarshal (XML string → Java object) in a message sink
JAXBContext ctx = JAXBContext.newInstance(OrderStatusType.class);
OrderStatus status = ((OrderStatusType) ctx.createUnmarshaller()
        .unmarshal(new InputStreamReader(new ByteArrayInputStream(msg.getBytes())))).getOrderStatus();

// Marshal (Java object → XML string) in KitCompleteService
Marshaller m = JAXBContext.newInstance(BailmentStockRequest.class).createMarshaller();
m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
m.marshal(request, stringWriter);
```
**Why it matters:** SAP speaks XML. JAXB binds XML schemas to Java classes so we work with typed objects, not string parsing. `ObjectFactory` builds `JAXBElement` wrappers for the root element.

**Interview gotcha:** JAXB = XML binding (`@XmlRootElement`, `@XmlElement`); Jackson/Gson = JSON. Know marshal (write) vs unmarshal (read). For REST responses BDSI uses Gson/Jackson.

---

## 11. Date/Time (a concept I actually debugged)
**What it is:** Parsing/formatting dates. Legacy `SimpleDateFormat`/`Date` vs modern `java.time`.

**BDSI example + the bug:**
```java
DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
Date d = df.parse(value);          // throws if 'value' uses '-' instead of '/'
```
In the replenishment report, dates arrived as `MM-dd-yyyy` (dashes) but the parser expected `/`. Parsing threw, the code fell back to writing the value as **text**, so Excel showed left-aligned `08-24-2026` that couldn't be filtered as a date. The fix: parse with the correct pattern, write a real `java.util.Date` (numeric Excel cell), and use a locale-proof display format so `/` always shows.

The JWT code shows the modern API:
```java
Instant now = Instant.now();
now.plus(Duration.ofMinutes(expiryMin));   // immutable, thread-safe
```

**Interview gotcha:** `SimpleDateFormat` is mutable and **not thread-safe** — a top-3 concurrency bug source. Prefer `java.time` (`LocalDate`, `Instant`, `DateTimeFormatter`) which is immutable and thread-safe. Time zones matter — `Instant` is UTC.

---

## 12. equals() vs == (another bug I fixed)
**What it is:** `==` compares references (same object); `.equals()` compares logical value.

**BDSI example:** The report marks the confirmed ship date red if it differs from the required date. Comparing the raw formatted strings mis-fired when separators differed (`08/24/2026` vs `08-24-2026` look "different" as strings but are the same date). The correct comparison is on parsed `Date` values:
```java
if (!confirmed.equals(required)) { /* highlight red */ }   // value comparison
```
**Interview gotcha:** For `String`/wrappers/dates always use `.equals()`. `==` on autoboxed `Integer` is a classic trap (cached -128..127). If you override `equals()` you must override `hashCode()` (contract) — important for map/set keys.

---

## 13. static vs instance, final, constants
**What it is:** `static` members belong to the class (one copy shared); instance members belong to each object. `final` = can't be reassigned.

**BDSI examples:**
```java
private static final String REPORT_DATE_FORMAT = "MM/dd/yyyy";  // one shared constant
public static List<Field> getAllFields(Class<?> t) { ... }       // stateless utility → static
private void setCellValue(Cell cell, ...) { ... }                // uses per-object config → instance
```
**Why it matters:** Constants and stateless helpers should be `static` (no reason to tie them to an instance). `setCellValue` reads instance config (`transactionDataMap`) so it's an instance method.

**Interview gotcha:** static state is shared across threads → must be immutable or synchronized. `static final` constants are safe; a mutable `static` field is a concurrency hazard.

---

## 14. Enums
**What it is:** A fixed set of typed constants.

**BDSI example:** Transaction `type` (STR, WAERCONS, ...) and `state` (CONFIRMED, SHIPPED, FAILED) are enumerated in the repository; base has a `ServiceName` enum for SAP endpoints. In Java you'd model these as `enum` for type safety instead of magic Strings/ints.

**Interview gotcha:** enums are singletons, can have fields/methods, work in `switch`, and are safer than String codes (compile-time checking, no typos).

---

## 15. String handling
```java
String fileName = "Uploaded_Replenishment_Orders_" + customerNumber + "_" + timestamp + ".xlsx";
String subject  = MessageFormat.format("[BSH{0}] {1} - Uploaded Replenishment Notifications", envSuffix, customerName);
boolean ok = StringUtils.isNotBlank(value);   // null-safe
```
**Why it matters:** `MessageFormat` builds the subject cleanly (env suffix + customer). `StringUtils.isNotBlank` avoids `NullPointerException` on null strings.

**Interview gotcha:** String is immutable; concatenation in loops creates garbage — use `StringBuilder`. `==` on strings compares references (interning trap) — use `.equals()`.

---

## 16. Design patterns present (name them with evidence)

| Pattern | Where in BDSI | One-line why |
|---------|---------------|--------------|
| Dependency Injection | ATG `.properties` wiring (+ Spring in feed) | decouples construction from use |
| Template Method | `SingletonSchedulableService.doScheduledTask` → subclass `performTask()` | base defines skeleton, subclass fills steps |
| Strategy / Polymorphism | `WMQMessageSink` sinks; `FeedProcessor`s | swap behavior without `if/else` |
| Factory | JAXB `ObjectFactory`; `WMQJMSInitialContextFactory` | centralize object creation |
| Producer/Consumer | MQ source (producer) + sink (consumer) | decouple via a queue |
| Singleton (clustered) | `SingletonSchedulableService` | one active instance in a cluster |
| Adapter | `SapInventoryCacheAdapter`, `ScrapConsignmentInventoryAdapter` | bridge incompatible interfaces |
| Builder | POI workbook, `Jwts.builder()`, HttpClient builder | step-by-step immutable construction |
| Facade | `ConsignmentTransactionTools`, `BinTransactionManager` | simple front over a complex subsystem |

---

## 17. Java memory & performance (explained, not just listed)

### 17.1 Connection pooling — `BDSISapEndpoint`
**The problem:** Opening a new HTTPS connection to SAP for every call is expensive — TCP handshake + TLS handshake (certificate exchange) each time. Under load that kills throughput and exhausts sockets.
**What the code does:**
```java
PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
cm.setMaxTotal(maxConnection);                 // cap total open connections
cm.setDefaultMaxPerRoute(defaultConnectionPerRoute);  // cap per target host
HttpClients.custom().setConnectionManager(cm)
    .setConnectionTimeToLive(connTimeToLive, TimeUnit.MINUTES)   // recycle old connections
    .evictIdleConnections(maxIdleTime, TimeUnit.SECONDS)         // close idle ones
    .setDefaultRequestConfig(RequestConfig.custom()
        .setConnectTimeout(t).setSocketTimeout(t).build());      // never hang forever
```
**Why each setting matters:** `maxTotal`/`perRoute` bound concurrency so we don't overwhelm SAP or ourselves; `TTL` + `evictIdle` prevent stale/half-dead connections; timeouts prevent a slow SAP from blocking threads indefinitely. The client is created once and reused (double-checked locking with `volatile`), and closed on `doStopService()`.
**Say this:** "Reusing pooled, kept-alive TLS connections avoids repeated handshakes — big latency and stability win for a chatty SAP integration."

### 17.2 Batch caps — bounded work per run
**The problem:** If a scheduler loaded *all* pending rows at once, a backlog of 100k messages would blow up heap and run for hours.
**What the code does:** `OrderStatusMessageManager` caps each run: `maxRowCount = 800` (`rownum <= 800` in SQL) and `WMQSinkConnector` reads `maxMessageReadCount = 100` messages per poll.
**Why it matters:** Bounded batches keep memory flat and make each run finish predictably; the next scheduled run picks up the rest. This is back-pressure — never pull more than you can process.
**Say this:** "I bound batch size so memory and run time stay predictable regardless of backlog; the schedule drains the queue over multiple runs."

### 17.3 Resource cleanup — no leaks
**The problem:** POI workbooks, JDBC connections, file streams, and temp files all hold memory/handles. Leaking them causes `OutOfMemoryError` or "too many open files."
**What the code does:** try-with-resources for connections/statements/streams; `finally { workbook.close(); }`; temp report files are deleted after the email is sent (`reportFile.delete()`).
**Say this:** "Every heavyweight resource is closed deterministically — try-with-resources for auto-closeables, explicit close/delete for workbooks and temp files."

### 17.4 Caching — avoid repeated expensive lookups
**The problem:** Fetching a secret from Azure Key Vault is a network call; doing it on every message would add latency and cost.
**What the code does:** `AzureKeyVaultService` caches secrets in a `ConcurrentHashMap` (`computeIfAbsent`), so each secret is fetched once and reused. Cardex change detection uses **MapDB** (an off-heap/disk-backed map) to remember previously seen records without holding everything in heap.
**Say this:** "Secrets are cached in a thread-safe map so we hit Key Vault once per secret; large change-detection state uses MapDB to stay off the Java heap."

### 17.5 GC / heap awareness (bonus talking point)
Large Excel/PDF generation and big result sets are the main heap pressure. Mitigations here: bounded batches, streaming reads (`ResultSet` row-by-row), closing workbooks promptly. For very large exports, streaming POI (`SXSSFWorkbook`) or writing straight to a file/S3 would reduce peak heap — a good "what I'd improve" answer.

---

## 18. Self-test questions (answer out loud)
1. Walk through a stream pipeline you wrote and what each stage does.
2. Why is `SimpleDateFormat` unsafe across threads, and what replaces it?
3. `==` vs `.equals()` — give the date-comparison example.
4. How does try-with-resources prevent leaks, and what interface enables it?
5. Interface vs abstract class — contrast `WMQMessageSink` and `GenericBulkLoadService`.
6. Where did you use reflection, and what are its three downsides?
7. `ExecutorService` vs `parallelStream` vs `CompletableFuture` — when each?
8. Explain connection pooling and why it matters for the SAP calls.
9. Why bound batch size in a scheduler? What problem does it prevent?
10. How would you make a shared scheduled job run once in a cluster?
