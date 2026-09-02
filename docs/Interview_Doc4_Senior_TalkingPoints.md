# Doc 4 — Senior Talking Points, STAR Stories & Spring/Azure Mapping

For an 11-YOE engineer targeting **Spring Boot / Microservices / Azure**. Grounded in the full BDSI system, framed honestly.

> **Opening framing:** "My project is a large Oracle ATG commerce monolith for Boeing Distribution — B2B consignment inventory integrated with SAP over IBM MQ, being modernized onto Azure. I've worked across backend services, async SAP integration, batch/scheduled processing, reporting, and security. I understand these patterns deeply and map them directly to Spring Boot microservices."

---

## PART A — System-design talking points (explained, with whole-system evidence)

### A1. Async, event-driven enterprise integration
**The point:** ATG and SAP are integrated entirely asynchronously over IBM MQ. ATG publishes an order to a queue and returns immediately; SAP processes it on its own time and publishes status events (confirmed, blocked, shipped, invoiced) back to response queues, which `OrderStatusMessageManager` consumes and dispatches to typed `WMQMessageSink` handlers.
**Why this is a strong senior answer:** it demonstrates you understand *why* async — it decouples the two systems so SAP downtime or slowness never fails a customer checkout; the queue buffers traffic and preserves it; and processing can be parallelized on the consumer side. You can then discuss ordering, at-least-once delivery, and how you separate recoverable failures (retry) from fatal ones (fail fast).
→ **Spring/Azure mapping:** Spring JMS/`@JmsListener`, Spring Cloud Stream, **Azure Service Bus** (queues/topics, DLQ, duplicate detection), or Kafka for high-throughput event streaming.

### A2. Idempotency & "process once" in a distributed system
**The point:** Because delivery is at-least-once and jobs run repeatedly, the same message/order could be processed twice. BDSI prevents that with explicit state flags: MQ messages carry `is_processed` (0=new, 3=in-progress, 1=success, 2=retry) and notifications carry `is_email_sent`. The dispatcher also de-duplicates within a batch by extracting the SAP order number from the XML, and it updates the state flag **inside a transaction** only after the work succeeds.
**Why this matters:** "How do you guarantee exactly-once / avoid duplicate side effects?" is one of the most common senior distributed-systems questions, and you have a concrete, real answer.
→ **Spring/Azure mapping:** idempotency keys, the transactional outbox pattern, Service Bus duplicate detection, `@Transactional`.

### A3. Reliability & resilience
**The point:** Failures are expected and handled deliberately. Recoverable failures (e.g. a confirmation arrives before the order is loaded) are marked "retry" and re-attempted on the next run; if a message keeps failing beyond `numberOfDaysToRetry` (2 days) it's escalated to a failure report/email instead of retrying forever (poison-message handling). The SAP HTTP client (`BDSISapEndpoint`) uses connection pooling, per-request timeouts, idle-connection eviction, and connection TTL so a slow SAP can't hang threads, plus an async "SAP service down" alert.
**Why this matters:** shows you design for the unhappy path, not just the happy path.
→ **Spring/Azure mapping:** Resilience4j (retry, circuit breaker, bulkhead, timeout), dead-letter queues, health probes.

### A4. Cluster-safe scheduling (a subtle but important point)
**The point:** In a multi-node cluster every node has the same scheduler, so a naive job would fire on all nodes and produce duplicate emails/orders. `SingletonSchedulableService` uses a distributed lock (`ClientLockManager`) so exactly one node acquires the lock and runs the job; the rest skip.
**Why this matters:** it's a classic "gotcha" — many engineers forget that `@Scheduled` fires on every instance. Naming the problem and the fix signals maturity.
→ **Spring/Azure mapping:** `@Scheduled` + **ShedLock** (a JDBC/Redis-backed lock). Say ShedLock by name.

### A5. Batch processing at scale
**The point:** Two batch families for two needs. Business jobs (notifications, reports, file processing, cleanup) are dozens of ATG schedulers. Large data feeds (product/inventory/price) use **Spring Batch** with chunk-oriented steps, tasklets, deciders, partitioned parallelism, and a `JtaTransactionManager` for restartable, high-volume processing.
**Why this matters:** you can speak to both simple scheduled jobs and heavy, restartable, partitioned batch — and Spring Batch is genuine hands-on experience here.
→ **Spring/Azure mapping:** Spring Batch on AKS/Functions; chunk vs tasklet steps; partitioning; JobRepository restartability.

### A6. Secrets & security maturity
**The point:** No secrets live in code or config. The MQ JKS keystore password, the JWT signing key, and the SAP APIM client secret are all fetched at runtime from **Azure Key Vault**, and the app authenticates to Key Vault using **Managed Identity** (its own Azure identity) — so there is nothing to store or manually rotate. Auth is layered: Ping SSO for identity, Twilio MFA for assurance, JWT for the stateless session, and CSRF filters for request integrity.
**Why this matters:** secrets management + defense-in-depth is exactly what security-conscious (especially aerospace/defense) interviewers probe. Bonus honesty point: some legacy config still has hardcoded keys, which I'd migrate to Key Vault.
→ **Spring/Azure mapping:** Spring Cloud Azure Key Vault property source; Spring Security resource server + JWT.

### A7. Configuration & environment strategy
**The point:** One codebase runs in dev, eight staging environments, and prod without branching. ATG layers configuration: base config → `env/<env>` overrides → `node/<role>/<env>` overrides, merged by precedence at startup. So a queue manager, URL, secret name, or feature toggle differs by **config**, never by code.
**Why this matters:** clean environment promotion and auditable releases — the platform-level equivalent of Spring profiles + Config Server.
→ **Spring/Azure mapping:** Spring profiles, Spring Cloud Config Server / Azure App Configuration, externalized config.

### A8. Legacy-to-cloud modernization (lead framing)
**The point:** It's a large ATG monolith being incrementally modernized onto Azure. As a lead I can articulate a decomposition: carve out bounded-context services — Order, Inventory/Consignment, Notification, and an Integration service acting as an **anti-corruption layer** to SAP — communicating via async events, fronted by an API gateway, with centralized secrets (Key Vault) and observability (App Insights/tracing). Migrate with the **strangler-fig** pattern (route slices of traffic to new services while the monolith shrinks).
**Why this matters:** modernization strategy is a staff/lead conversation; you can ground it in a system you actually know.
→ **Microservices mapping:** bounded contexts, API gateway, service discovery, distributed tracing, strangler-fig migration.

---

## PART B — STAR behavioral stories (memorize 4–5)

### STAR 1 — Excel date bug (root-cause debugging)
- **S:** Stage report showed ship dates as text `08-24-2026`; spec required real, filterable `mm/dd/yyyy` dates.
- **T:** Produce real Excel date values displaying with slashes across locales.
- **A:** Traced it — parser expected `MM/dd/yyyy` but data was `MM-dd-yyyy` so parse failed → text fallback; Excel also localizes the separator. Wrote a real `java.util.Date` (numeric cell) with a date `CellStyle` and a locale-proof quoted format; compared against the HAECO report for consistency; verified via a POI reader (cell type NUMERIC, dateFormatted true).
- **R:** Sortable/filterable dates, correct display everywhere.

### STAR 2 — Caught a prod-impacting gap early (systems thinking)
- **S:** Notification emails were to go to the uploader, read from a DB column.
- **T:** Confirm that column actually holds a valid email in prod.
- **A:** Traced the upload/file-processor path; it never captured the uploader's email → prod sends would fail. Raised it with the upload feature owner to persist a dedicated email field; kept my scheduler ready to consume it.
- **R:** Prevented a production defect before release.

### STAR 3 — Delivered SH-7690 by reusing proven patterns
- **S/T:** Build automated Excel+email notification after replenishment uploads.
- **A:** Modeled the scheduler on `GKNConsumptionNotificationScheduler`/`ImmASNReportScheduler`; reused `SHEmailTools` prod/test recipient logic; added `is_email_sent` idempotency and a 15-min delay window for SAP replies; matched the HAECO report's date approach.
- **R:** Consistent, reviewable, maintainable feature aligned with sibling notifications.

### STAR 4 — Safe refactor / dead-code removal
- **S:** After moving to scheduler-based sending, an older upload-time send method lingered.
- **A:** Searched all callers (none), removed the method + unused field + config injection, verified no compile errors/dangling refs, proposed change + risk before applying.
- **R:** Cleaner code, zero regressions.

### STAR 5 — Git cherry-pick to UAT under pressure
- **S/T:** Move specific fixes to a UAT branch amid diverging branches/merge conflicts.
- **A:** Identified exact commits, cherry-picked in order, resolved content conflicts (kept correct incoming versions, merged where both needed), verified, pushed; also diagnosed an auth failure (expired credential) blocking fetch.
- **R:** UAT updated correctly with no collateral changes.

---

## PART C — ATG → Spring Boot / Microservices / Azure mapping

| BDSI (ATG) | Spring Boot / Microservices / Azure equivalent |
|------------|-----------------------------------------------|
| Nucleus `.properties` component wiring | `@Component/@Service/@Repository` + constructor injection; `application.yml` |
| `SingletonSchedulableService` + cluster lock | `@Scheduled` + **ShedLock** |
| IBM MQ producer/sink (JMS) | Spring JMS (`JmsTemplate`, `@JmsListener`), Spring Cloud Stream, **Azure Service Bus** |
| `OrderStatusMessageManager` type-routed sinks | Strategy beans + router; Spring Integration `@ServiceActivator` |
| GSA repositories / raw JDBC | **Spring Data JPA**; `JdbcTemplate` (already used in feed module) |
| `BDSISapEndpoint` (pooled HttpClient) | `WebClient`/`RestTemplate` + connection pool; Feign client |
| `SHEmailTools` + JSP templates | `JavaMailSender` + Thymeleaf |
| `JwtHelper` (jjwt HS256) | Spring Security resource server + JWT |
| `AzureKeyVaultService` | Spring Cloud Azure Key Vault property source |
| `AzureBlobService` | Spring Cloud Azure Storage `BlobServiceClient` |
| `S3FileManager` + Parquet | Spring Cloud AWS S3; same POI/Parquet libs |
| Jersey `@RestResource` | `@RestController` + `@GetMapping/@PostMapping` |
| Spring Batch feeds (real) | **Direct Spring Batch experience** — chunks, tasklets, partitioning |
| `env/`/`node/` config layering | Spring profiles / Config Server / Azure App Configuration |

> **Honesty note:** the platform is ATG, not Spring Boot microservices. Genuinely Spring in the repo: **Spring Batch + JdbcTemplate** (feed module), `springframework.util` helpers, a couple of `@RequestMapping` REST resources. **Azure (Key Vault/Blob/Managed Identity)** and **AWS S3/Parquet** are real. Present microservices as "patterns I know and would apply," not "already built here."

---

## PART D — Senior system-design Q&A (project as evidence)

**Prevent duplicate processing?** State flags + dedup keys (my `is_processed`/`is_email_sent`, SAP-order# de-dup); in Spring: idempotency keys / outbox / Service Bus dedup.

**Run a scheduled job once across instances?** Distributed lock (ATG `ClientLockManager`; Spring: ShedLock).

**Sync vs async?** Async (MQ) for decoupling/resilience (SAP order flow); sync (REST via APIM) for immediate lookups (kit info, docs). I also delay a notification job so async replies land first.

**Manage secrets?** Vault at runtime — Azure Key Vault via Managed Identity for JKS/JWT/APIM secrets.

**Decompose this monolith?** Bounded contexts (Order, Inventory/Consignment, Notification, SAP-integration adapter), async events between them, gateway in front, anti-corruption layer to SAP, observability + tracing, strangler-fig incremental migration.

**Reliability of integrations?** Retry windows, poison-message escalation, pooled/timed HTTP, TLS mutual auth, transactional status updates.

**Handling large data/reports?** Batch caps (100/800), streaming, temp-file cleanup, Spring Batch partitioning for feeds, Parquet on S3 for analytics.

---

## PART E — Gaps to close before interview (study these)
Spring Boot (auto-config, starters, profiles, actuator) • Spring Web (`@RestController`, `@ControllerAdvice`, validation) • Spring Data JPA (entities, derived queries, transactions, N+1) • Spring Security + JWT • Microservices (Eureka, Spring Cloud Gateway, Config Server, Resilience4j, tracing, Feign) • Messaging (Kafka vs Service Bus vs RabbitMQ, consumer groups, DLQ, idempotency) • Azure (App Service/AKS, Service Bus, Blob, Key Vault, Managed Identity, App Insights, Functions) • Containers/K8s (Docker, probes) • Testing (JUnit5, Mockito, `@SpringBootTest`, Testcontainers) • Spring Batch (you have real exposure — be ready to go deep).

---

## PART F — Two-paragraph project summary (memorize)
"I work on BDSI, a large B2B e-commerce and consignment-inventory platform for Boeing Distribution built on Oracle ATG. Aerospace customers hold Boeing-owned consignment stock; the platform manages catalog, ordering, replenishment, bin scanning, and kitting, and integrates deeply with SAP/S4HANA — asynchronously over IBM MQ for the order lifecycle (create, confirm, block, ship, invoice) and synchronously over an IBM API Connect REST gateway for lookups. It's a multi-module ATG app (~20 modules) with a storefront, a backend services hub, a fulfillment/messaging module, kitting, and Spring Batch catalog feeds. It's being modernized onto Azure — we use Key Vault and Managed Identity for secrets, Blob for files, and AWS S3/Parquet for analytics.

My focus is the ServiceHub module: schedulers, file processors, Excel/PDF reporting, and email notifications, plus the SAP/MQ integration and Azure security. Recently I delivered an automated replenishment notification (SH-7690) — a cluster-safe scheduler that, after a batch of orders is uploaded, builds an Excel report including SAP confirmation data and emails the submitter, with idempotent transactional status tracking and a delay window so async SAP replies are included. I can map every ATG pattern here — DI, repositories, schedulers, messaging, security — to its Spring Boot / microservices / Azure equivalent."
