# Doc 3 — Integrations Deep-Dive (explained clearly, with real code)

Each integration: **what it is → why BDSI uses it that way → how it works step by step → real code → interview talking points/gotchas.** Read the prose to actually understand it.

---

## 1. IBM MQ (WebSphere MQ) / JMS — the SAP async backbone

### What it is and why
IBM MQ is enterprise messaging middleware; JMS is the standard Java API to talk to it. "Async" means the sender drops a message on a **queue** and moves on — it does not wait for SAP to respond. BDSI chose async because SAP order processing is slow and sometimes down; a queue **buffers** the traffic so nothing is lost and ATG threads aren't blocked waiting on SAP.

### How the flow works, step by step
1. **Outbound:** When an order is created, `OrderFulfillmentMessageSource` serializes it to XML and puts a JMS `TextMessage` on the request queue `ECOMMERCE_ORDER_INTERFACE`. SAP reads it later.
2. **SAP processes** and, over time, produces status events (confirmed, blocked, shipped, invoiced) onto response queues.
3. **Inbound read:** `WMQSinkConnector` (using `WMQJMSInitialContextFactory`) connects to the response queue and reads up to `maxMessageReadCount` (100) messages per poll.
4. **Stage:** messages are stored in DB tables (`order_cb_message`, `order_shipped_message`, etc.) with `is_processed = 0`.
5. **Dispatch:** the scheduled `OrderStatusMessageManager` fetches unprocessed rows and routes each to the correct **sink** based on message type.
6. **Apply:** the sink (e.g. `OrderConfirmedStatusMessageSink`) unmarshals the XML and updates the ATG order (SAP order#, ship dates, state).

### Security (how the TLS connection is built)
Connections use **TLS with a client certificate** (mutual auth). The keystore/truststore are JKS files; the JKS password is **not in config** — it's fetched from **Azure Key Vault** at runtime, Base64-decoded, and used to load the keystore. `USER_AUTHENTICATION_MQCSP=false` means auth is by certificate, not userid/password.

### Producer code (annotated)
```java
MQQueueConnectionFactory cf = new MQQueueConnectionFactory();
cf.setStringProperty(WMQConstants.WMQ_HOST_NAME, hostName);        // mqgate.boeing.com
cf.setIntProperty(WMQConstants.WMQ_PORT, portNumber);             // 13144 prod / 13143 stg
cf.setStringProperty(WMQConstants.WMQ_CHANNEL, channel);          // CLIENT.ATG_AI_SEND
cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT); // client (network) mode
cf.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, queueManager); // MQSOPPHX / MQSOFSTL / MQSODSTL
cf.setStringProperty(WMQConstants.WMQ_SSL_CIPHER_SUITE, cipherSuite); // TLS cipher
cf.setSSLSocketFactory(sslSocketFactory);                        // built from JKS + Key Vault password

MQQueueConnection con = (MQQueueConnection) cf.createQueueConnection();
MQQueueSession s = (MQQueueSession) con.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
MQQueueSender sender = (MQQueueSender) s.createSender((MQQueue) s.createQueue("queue:///" + queueName));
con.start();
sender.send(s.createTextMessage(orderXml));   // the order as XML
```
**JMS object chain to memorize:** ConnectionFactory → Connection → Session → Destination (Queue) → Producer/Consumer → Message.

### The idempotency / retry state machine (important senior point)
The staged message has an `is_processed` column that acts as a state machine:
```
0 = new (not yet processed)
3 = in-progress (a run has claimed it)
1 = success
2 = retry (a recoverable failure — try again next run)
```
`OrderStatusMessageManager` also **de-duplicates** within a batch by pulling the SAP order number out of the XML (`<sapOrderNumber>` / `<sapSalesDocument>`), so two messages for the same order aren't both applied. If a message keeps failing for more than `numberOfDaysToRetry` (2) days, it's escalated to a failed-status report/email instead of retrying forever.
**Why it matters:** This is how you get "process each message once and only once, and don't get stuck on a poison message" — a top distributed-systems interview topic.

### Sample XML — outbound order and inbound confirmation
```xml
<!-- ATG → SAP -->
<Order><Header><atgOrderId>o12345678</atgOrderId><customerNumber>9000007750</customerNumber>
  <orderType>CMP_ORDER</orderType></Header>
  <LineItems><LineItem><lineNumber>10</lineNumber><primePartNumber>PN-001</primePartNumber>
    <quantity>10</quantity><plant>8000</plant><storageLocation>2000</storageLocation></LineItem></LineItems>
</Order>

<!-- SAP → ATG -->
<OrderStatusType><OrderStatus><atgOrderId>o12345678</atgOrderId>
  <sapOrderNumber>1234567890</sapOrderNumber><statusCode>CONFIRMED</statusCode>
  <LineDetails><lineNumber>10</lineNumber>
    <ScheduledDelivery><qty>10</qty><confirmedShipDate>08/20/2026</confirmedShipDate></ScheduledDelivery>
  </LineDetails></OrderStatus></OrderStatusType>
```

### The sinks (one class per SAP event type)
`OrderConfirmedStatusMessageSink`, `OrderBlockStatusMessageSink`, `OrderShippedStatusMessageSink`, `OrderInvoiceMessageSink`, `OrderChangeMessageSink`, `OrderDeleteMessageSink`, `OrderSOCompletionMessageSink`, `POOrderStatusMessageSink`, `POChangeOrderStatusMessageSink`, `DropShipPOMessageSink`, `OpenOrdersMessageSink`, `CustomerContractMessageSink`. Adding a new event type = new sink + config entry; the dispatcher never changes (Open/Closed Principle).

### Talking points / gotchas
- **Why not synchronous REST for orders?** Resilience — SAP downtime or slowness must not fail the customer's checkout. The queue absorbs it.
- **Queue vs Topic?** Queue = point-to-point (one consumer gets each message); Topic = pub/sub. BDSI uses queues.
- **`AUTO_ACKNOWLEDGE`** removes the message once delivered to the consumer; combined with DB staging + `is_processed`, we don't lose messages.

---

## 2. SAP / S4HANA — three integration mechanisms

### Why three?
Different needs. Order lifecycle is fire-and-forget-with-callbacks → async MQ. Lookups (kit info, documents, ship-to) need an **immediate answer** → synchronous REST. Some legacy SAP services only speak SOAP.

### (a) Async via IBM MQ — see §1.

### (b) Synchronous REST via IBM API Connect (APIM) — `BDSISapEndpoint`
This is the production HTTP client to SAP, and it's a great "how do you call an external system properly" story.
```java
// One reused, pooled TLS client (created lazily, double-checked with volatile)
PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
cm.setMaxTotal(maxConnection);                        // cap total sockets
cm.setDefaultMaxPerRoute(defaultConnectionPerRoute);  // cap per SAP host
closeableHttpClient = HttpClients.custom().setConnectionManager(cm)
    .setConnectionTimeToLive(connTimeToLive, TimeUnit.MINUTES)  // recycle old conns
    .evictIdleConnections(maxIdleTime, TimeUnit.SECONDS)        // drop idle conns
    .setDefaultRequestConfig(RequestConfig.custom()
        .setConnectTimeout(t).setSocketTimeout(t).build())      // never hang forever
    .build();

// Per-request: APIM auth headers + secrets from Key Vault + a trace id for cross-system tracing
post.addHeader("X-IBM-Client-Id", clientId);
post.addHeader("X-IBM-Client-Secret", azureKeyVaultService.getSecret(clientSecret));
post.addHeader(apimKeyName, azureKeyVaultService.getSecret(apimKeyValue.get(apimServerKey)));
post.addHeader("traceID", UUID.randomUUID().toString());
```
- **IBM API Connect** sits in front of SAP as a gateway; BDSI authenticates with a client id/secret pair (secrets from Key Vault).
- A **non-200** response throws a `RuntimeException` and can fire an async "SAP service down" alert email (on a separate thread so it doesn't block the caller).
- Uses: kit info, cardex documents (PDF certificates), ship-to detail, EHS/GTS/incoterm, scrap inventory, user profile.

### (c) SOAP (Axis2 / wsdl4j)
Legacy SAP web services with a WSDL contract; `B2BOrderGateway` is a JAX-WS order gateway. XML bound with JAXB.

### SAP request/response building with JAXB (kitting example)
```java
Marshaller m = JAXBContext.newInstance(BailmentStockRequest.class).createMarshaller();
m.marshal(request, writer);                                   // Java object → request XML
BailmentStockResponse resp = (BailmentStockResponse) JAXBContext
    .newInstance(BailmentStockResponse.class).createUnmarshaller().unmarshal(reader); // XML → object
```

### Talking points / gotchas
- **SAP order# vs ATG order#:** ATG creates the order and gets the SAP order# back on confirmation; for **SAP-FAILED** orders (no SAP#), reports show the ATG order# instead.
- **traceID header:** every SAP call carries a UUID for end-to-end tracing across ATG↔APIM↔SAP — great observability answer.
- **Connection pooling + timeouts** are the difference between a resilient integration and one that hangs threads when SAP is slow.

---

## 3. Security — JWT, SSO, MFA

### 3.1 JWT (JSON Web Token) — `JwtHelper`, jjwt, HS256
**What it is:** A signed token (`header.payload.signature`) that carries claims (e.g. the user's email as `subject`) plus issue/expiry times. The server verifies the **signature** to trust the token — no server-side session needed (stateless).

**How BDSI creates and verifies it:**
```java
// create — sign the claims with a secret key
String token = Jwts.builder().setSubject(email)
    .setIssuedAt(Date.from(Instant.now()))
    .setExpiration(Date.from(Instant.now().plus(Duration.ofMinutes(expiryMin))))
    .signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();

// verify — parseClaimsJws throws if signature is wrong or token expired
String subject = Jwts.parserBuilder().setSigningKey(getSigningKey())
    .build().parseClaimsJws(token).getBody().getSubject();
```
**The signing key comes from Key Vault** (never hardcoded):
```java
String decryptedSecret = azureKeyVaultService.getSecret(secret);
return new SecretKeySpec(decryptedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
```
**Uses:** storefront auth cookie (Secure, SameSite=None, Max-Age), password-reset links, unsuccessful-login emails. `SecurityTools` additionally uses **auth0 java-jwt** for API-client tokens.

**Why HS256:** symmetric HMAC — the same secret signs and verifies. Simple and fast for a single trusted issuer. (RS256 would be asymmetric — sign with a private key, verify with a public key — used when many parties must verify but not sign.)

### 3.2 Ping Identity SSO (`com.ecom.pingone`, `PingTools`)
Enterprise single sign-on. BDSI validates Ping-issued JWTs, decodes the payload, and transfers user context into the ATG session — so users log in once at the corporate IdP.

### 3.3 Twilio MFA (`com.bdsi.twilio.mfa`)
Multi-factor authentication (one-time codes via Twilio) layered on login for extra assurance.

### 3.4 JAX-RS security filters (servicehub)
`BDSICsrfProtectionFilter` (CSRF tokens), `BDSILoggedInAccessChecker` (authz), `BDSIPostMatchingContextFilter`, `BDSIRestExceptionMapper` (uniform error responses).

### Talking points / gotchas
- **Stateless vs session:** JWT avoids server session storage → scales horizontally (any node can verify). Downside: you can't easily revoke a token before expiry (mitigate with short expiry + a denylist if needed).
- **Never trust an unverified token** — always verify the signature and check `exp`.
- **Layered auth:** SSO (identity) + MFA (assurance) + JWT (session) + CSRF (request integrity).

---

## 4. File Mapping Network framework (config-over-code B2B files)

### The problem it solves
BDSI exchanges files with many customers (open orders, consumption, ASN, invoices). Each customer wants a **different layout** — different columns, order, delimiter, header/footer, filename, frequency. Hardcoding a parser/generator per customer would be unmaintainable.

### How it works
Admins define the layout as **data** in a repository: direction (inbound/outbound), file type, delimiter, column list (with order and mapping), filename format, frequency, active flag. A generic engine then reads that config to **parse** inbound files into transactions/orders, or **generate** outbound files, and pushes/pulls them over SFTP or Azure Blob.
```java
class FileMappingNetworkReqVO {
  String fileDirection;   // INBOUND / OUTBOUND
  String fileType;        // CSV / TXT / fixed-width
  String fileNameFormat;  // e.g. Openorder_{timestamp}.txt
  String delimiter;       // "," or tab
  String frequency;       // daily / "after scancode processing"
  List<FileColumns> columns;  // ordered, mapped columns
  boolean active;
}
```
Processors: `FileMappingInboundFeedProcessor`, `FileMappingOutboundFeedProcessor`, `GenericFileMappingProcessor`. Schedulers drive it by frequency (`FileMappingFeedScheduler`, `InboundFileProcessScheduler`, `OutboundFileProcessScheduler`, `MFTSFileMappingScheduler`), and a purge scheduler cleans logs.

### Talking points
- **Config over code:** onboard a new customer/file by adding config rows, not by writing and deploying Java. This is the extensibility story interviewers like.
- A processing-log gives an audit trail of every file run.

---

## 5. Spring Batch — high-volume catalog/merch feeds (feed module)

### What it is and why
Product, inventory, and price feeds process **huge** volumes. Spring Batch is the standard for chunk-oriented batch: read a chunk, process it, write it, commit, repeat — with restartability and partitioning for parallelism.

### How BDSI uses it
```xml
<!-- batchConfig.xml -->
<bean id="transactionManager" class="org.springframework.transaction.jta.JtaTransactionManager"/>
<jee:jndi-lookup jndi-name="java:/JobRepositoryDS" id="jobRepository-dataSource"/>
```
- Framework module `PARALMerchModule`: `AbstractFeedItemReader` (read), `FeedItemChangeDetectorProcessor` (skip unchanged rows), `FeedRepositoryItemWriter` (write to ATG), ~25 tasklets (create/deploy project, download/validate file), `PartitionManager`/`RangePartitioner` (split work across threads), `BatchInvoker`, `FeedScheduler`, and Spring `JdbcTemplate` for DB reads.
- Concrete feeds in `PARALCatalogMerchModule`: catalog, inventory, price.

### Key Spring Batch concepts to explain
- **Chunk processing:** read N items → process → write the chunk in one transaction. If it fails, only that chunk rolls back and can restart.
- **Job / Step / Tasklet:** a Job has Steps; a Step is either chunk-oriented or a single Tasklet (e.g. download a file).
- **Partitioning:** split the input into ranges processed in parallel → throughput.
- **JobRepository:** stores job/step execution state so a failed job can **restart** where it stopped.
- **`JtaTransactionManager`:** XA transactions spanning the DB and other resources.

### Talking points
This is your **genuine Spring experience** in the project. Be ready to discuss chunk vs tasklet steps, restartability, and partitioning for large feeds.

---

## 6. Azure — Key Vault, Blob, Managed Identity

### Why
The platform is modernizing onto Azure. The security principle is **no secrets in code or config** — fetch them at runtime from a vault, authenticating with the app's own cloud identity.

### Key Vault (`AzureKeyVaultService`)
```java
secretClient = new SecretClientBuilder()
    .vaultUrl(vaultUrl)                                        // https://bdsipro-...kv.vault.azure.net/
    .credential(new DefaultAzureCredentialBuilder().build())  // Managed Identity — no stored creds
    .buildClient();
String value = secretClient.getSecret(name).getValue();       // cached in a ConcurrentHashMap
```
- **Managed Identity** (`DefaultAzureCredential`): Azure gives the running app an identity; it authenticates to Key Vault with that — so there are literally no credentials to store or rotate in the app.
- **Caching:** each secret is fetched once and cached (thread-safe map), avoiding a network call per use.
- **What's stored:** MQ JKS password, JWT signing key, SAP APIM client secret/keys.

### Blob Storage (`AzureBlobService`)
Stores/retrieves files (FTP blob, static, BDSI containers) — inbound/outbound file exchange and generated documents.

### Talking points / gotchas
- **Why Managed Identity over a service principal secret?** No secret to leak or rotate; Azure handles it.
- The codebase still has some **hardcoded keys in legacy config** (SendGrid/Maps) — a real "security debt I'd fix by moving to Key Vault" point.

---

## 7. AWS — S3 + Parquet

### Why
Analytics-scale data is written to S3 as **Parquet** (columnar) for downstream analytics/BI.
```java
S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build();
s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromFile(file));
```
- **`S3FileManager`** uses AWS SDK v2 and Apache Parquet/Avro/Hadoop.
- **Parquet** stores data by column, so analytics queries read only the columns they need and compress well (repeated values per column) — far cheaper than row-based CSV for big datasets.

**Talking point:** row format (CSV) vs columnar (Parquet): columnar wins for analytical scans; row wins for transactional row-at-a-time access.

---

## 8. REST / SOAP web services

### REST (Jersey / JAX-RS)
BDSI exposes many APIs for the CMP web UI, mobile app, and integrations.
```java
@GET @Path("/{id}") @Produces(MediaType.APPLICATION_JSON)
public Response get(@PathParam("id") String id) { return Response.ok(service.find(id)).build(); }
```
Examples: `CatalogRestResource`, `UomConversionRestResource`, `ReportAndMetricsRestResource`, `StagedReportsRestResource`, `AuditTrailRestResource`, consignment customer/inventory/minmax/order/transaction resources, KLX order REST (`CartRestResource`, `PurchaseOrderRestResource`), Ecom REST. Shared scaffolding in `jaxrsrest` (`APIResponse`, `BDSIRestExceptionMapper`); Swagger for docs.

### SOAP
`B2BOrderGateway` (JAX-WS, WSDL) and Axis2 clients for legacy SAP SOAP services.

### Talking points
- **REST vs SOAP:** REST = lightweight, JSON, HTTP verbs, stateless, easy for web/mobile; SOAP = XML envelope + WSDL contract + WS-* standards, used for formal/legacy enterprise services.
- JAX-RS annotations: `@GET/@POST`, `@Path`, `@PathParam`, `@Produces/@Consumes`; exception mappers give uniform error bodies.

---

## 9. Endeca search (b2bsearch)
Oracle **Endeca** powers faceted/guided **part search** over the aerospace catalog (`PartSearchTools`, `EndecaSearchResultsDroplet`, partial indexing). Endeca is a search/MDEX engine tuned for faceted navigation (filter by attributes) — important when catalogs have millions of parts with many attributes. (This is exactly where semantic/vector search could modernize it — see Doc 5.)

---

## 10. Google Analytics
Server-side **GA Reporting API v4** (`google-api-services-analyticsreporting`, google-api-client) pulls site metrics programmatically (server-to-server via Google API client, service-account/OAuth style). The storefront also embeds Google Maps/Fonts.

---

## 11. Barcode / PDF / Excel — document generation

| Purpose | Library | Where |
|---------|---------|-------|
| Excel | Apache POI (XSSF) | consignment/replenishment/HAECO/GKN reports |
| PDF/print | Apache FOP (XSL-FO→PDF) + PDFBox | labels, packing slips, certificates |
| Barcode | barcode4j + Batik (SVG) | bin labels, shipping |

**POI example (with the concepts that matter):**
```java
XSSFWorkbook wb = new XSSFWorkbook();
CellStyle date = wb.createCellStyle();
date.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("mm/dd/yyyy"));
cell.setCellValue(new SimpleDateFormat("MM/dd/yyyy").parse("08/20/2026")); // REAL date (numeric), filterable
cell.setCellStyle(date);
sheet.createFreezePane(1, 3);                                     // freeze header rows/cols
sheet.setAutoFilter(new CellRangeAddress(2, last, 0, cols));     // enable filtering
```
**Talking point (my real fix):** writing a real `java.util.Date` (not text) makes Excel treat it as a sortable/filterable date; a locale-proof format (`mm"/"dd"/"yyyy`) forces the `/` separator regardless of the machine's regional settings. **FOP** turns an XSL-FO template into a PDF; **barcode4j** renders barcodes as SVG via Batik.

---

## 12. Payments — SnapPay (`com.bdsi.snappay`)
Credit-card capture via a **hosted iframe** (`credit-iframe.css`, `UserCreditCardAuthorizer`). Card data is entered in SnapPay's iframe, not our pages, so sensitive PAN data never touches our servers — a **PCI-scope-reduction** design.

---

## 13. Scheduler-based jobs (ATG)

### What it is
Batch jobs on a timer, driven by ATG's `Scheduler`. The base class `SingletonSchedulableService` ensures the job runs on **only one node** in a cluster.
```java
public class UploadedReplenishmentNotificationScheduler extends SingletonSchedulableService {
  public void doScheduledTask(Scheduler s, ScheduledJob j) { if (isEnabled()) performTask(); }
}
```
```properties
scheduler=/atg/dynamo/service/Scheduler
schedule=every 15 minutes
clientLockManager=/atg/dynamo/service/ClientLockManager   # distributed lock
lockName=UploadedReplenishmentNotificationFeeds
enabled=false
```

### How "run once in a cluster" works
Multiple app nodes all have the scheduler, but before running, each tries to acquire a **cluster-wide lock** via `ClientLockManager`. Only the node that gets the lock runs the job; the others skip. Without this, every node would fire the job and you'd get duplicate emails/orders.

### Talking points / gotchas
- **Spring equivalent:** `@Scheduled` + **ShedLock** (a JDBC/Redis-backed lock) solves the exact same problem — say this by name.
- **Delay window:** many schedulers query rows "older than N minutes" so asynchronous SAP replies have landed before the report is built.
- **Idempotency:** a flag (`is_email_sent` / `is_processed`) updated transactionally after success prevents re-processing.

---

## 14. End-to-end integration story (one narrative to tell)
```
Storefront/CMP UI (JSP + REST; auth via JWT + Ping SSO + Twilio MFA)
  → ATG creates order → OrderFulfillmentMessageSource → IBM MQ → SAP
  → SAP status events → WMQSinkConnector → OrderStatusMessageManager → typed sinks → order updated
  → BDSISapEndpoint (sync REST via IBM API Connect) for lookups (kit info, cardex PDFs, ship-to, EHS)
  → Schedulers build Excel/PDF reports + emails; files move via SFTP / Azure Blob; analytics to AWS S3 (Parquet)
  → Catalog/price/inventory kept current via Spring Batch feeds; part search via Endeca
  → Every secret (MQ JKS pwd, JWT key, APIM secret) from Azure Key Vault via Managed Identity
```

---

## 15. Rapid-fire Q&A
- **What is JMS and why async for SAP?** Standard messaging API; async decouples ATG from slow/unreliable SAP and buffers traffic.
- **How is MQ secured?** TLS + client cert (JKS); password from Key Vault; client mode.
- **How does the message manager avoid duplicates/poison messages?** `is_processed` state machine + SAP-order# de-dup + retry cap → failure report.
- **Why pool HTTP connections to SAP?** Reuse kept-alive TLS connections; avoid handshake per call; bound concurrency; timeouts prevent hangs.
- **How is JWT verified and why stateless?** Re-check signature + `exp` with the Key Vault secret; no server session → horizontal scaling.
- **What does the File Mapping framework buy you?** Per-customer file formats via config, no code per customer.
- **Where's your real Spring experience?** Spring Batch catalog feeds (chunks, tasklets, partitioning) + `JdbcTemplate`.
- **Why Managed Identity + Key Vault?** No secrets in code; Azure-managed identity; central rotation.
- **Why Parquet on S3?** Columnar, compressed, cheap analytical scans.
- **Singleton scheduler?** Cluster lock so one node runs it (Spring: ShedLock).
