# Doc 1 — BDSI Commerce: Complete Project Overview

A full, interview-ready analysis of the entire BDSI platform (not just one feature). Use this to describe the system end-to-end: business, architecture, all modules, data, integrations, and flows.

---

## 1. Elevator pitch (30 seconds)

"BDSI is a large **B2B e-commerce and consignment-inventory platform for Boeing Distribution Services**, built on **Oracle ATG (Oracle Commerce)** in Java. It serves aerospace customers who hold Boeing-owned consignment stock at their own sites. The platform manages the full order lifecycle — catalog, cart, checkout, consignment replenishment, bin scanning, kitting — and integrates deeply with **SAP/S4HANA** (asynchronously over **IBM MQ**, and synchronously over an **IBM API Connect REST gateway**) for fulfillment, invoicing, and inventory. It's a multi-module ATG application (~20 modules, thousands of Java classes) being incrementally modernized onto **Azure** (Key Vault, Blob, Managed Identity), with **AWS S3/Parquet** for analytics, **Spring Batch** for catalog feeds, JAX-RS REST APIs, and JWT-based security."

---

## 2. Business domain

**Boeing Distribution** distributes aerospace parts. Many customers operate on a **consignment / VMI (Vendor-Managed Inventory)** model: parts physically sit at the customer's facility in labeled **bins**, but Boeing owns them until the customer consumes them. This drives the core business processes:

| Process | What happens |
|---------|--------------|
| **Catalog & ordering** | Customers browse parts and place B2B orders (web + punchout/B2B channels). |
| **Consignment replenishment (fillup)** | When bin stock drops below min, a **fillup order** refills it. Triggered by bin scans, uploaded files, or forecasts. |
| **Bin scanning** | Warehouse/customer scans bins (empty/full) to trigger replenishment and record consumption. |
| **Consumption reporting** | Customers report usage via files (Waerlinx/GKN, BAE, Cessna, Bell, 4PL, etc.). |
| **Kitting** | Assemble parts into kits (kit BOM, kit-complete, bailment stock) — integrated with SAP MDG. |
| **Order fulfillment** | ATG sends orders to SAP; SAP confirms, blocks, ships, invoices — status flows back asynchronously. |
| **Notifications & reporting** | Email + Excel/PDF reports for processed orders, exceptions, consumption, discrepancies. |

**Customer programs referenced in code:** TEXTRON, GKN (Filton/Waerlinx), HAECO, BAE, Cessna, Bell, Boeing 4PL, Aernnova, Agusta/Leonardo, Honeywell, Flight Star.

---

## 3. Technology stack

| Layer | Technology |
|-------|-----------|
| Commerce platform | Oracle ATG / Oracle Commerce 10.x (Nucleus components, GSA repositories, form handlers, droplets, pipelines, scenarios) |
| Language / build | Java 8; Gradle per-module builds; Groovy (base build) |
| Database | Oracle (ojdbc8); ATG GSA repositories + raw JDBC; Liquibase (glasir module) for schema management |
| Messaging | IBM WebSphere MQ over JMS (SAP integration) |
| Sync integration | IBM API Connect (APIM) REST gateway → SAP/S4HANA; SOAP (Axis2) for legacy services |
| Cloud (Azure) | Blob Storage, Key Vault, Managed Identity (DefaultAzureCredential), msal4j |
| Cloud (AWS) | S3 + Apache Parquet/Avro/Hadoop (analytics data) |
| Batch | Spring Batch (catalog/inventory/price feeds in the feed module) |
| REST | Jersey (JAX-RS) + Swagger; some Spring `@RequestMapping` |
| Search | Oracle Endeca (part search) |
| Reporting | Apache POI (Excel), Apache FOP + PDFBox (PDF), barcode4j + Batik (barcodes) |
| Security | JWT (jjwt HS256, auth0 java-jwt), Ping Identity SSO, Twilio MFA, CSRF filters |
| Analytics | Google Analytics Reporting API v4 |
| Payments | SnapPay (credit card), hosted iframe |
| Utilities | Lombok, Jackson, Gson, JAXB, JSch (SFTP), Reactor/Netty |

---

## 4. Module map (the whole application)

ATG assembles the app from modules; each has a `META-INF/MANIFEST.MF` with `ATG-Required` listing its dependencies.

| Module | Purpose |
|--------|---------|
| **base** | Core domain & platform (~800+ classes). Order domain (`KLXOrder`, managers), SAP integration (`BDSISapEndpoint`, MQ sources), MQ/JMS plumbing (`WMQSinkConnector`), security (`JwtHelper`, `AzureKeyVaultService`, `SecurityUtils`), Azure/AWS, Cardex feeds, personalization, pricing, and the large `com.klx.cmp.*` consignment logic. |
| **servicehub** | The backend services hub (my main area). Consignment inventory/transactions, replenishment, bin scanning, EHS feeds, audit, file processors, schedulers, reporting, notifications, and many JAX-RS REST resources. |
| **ful** | Fulfillment/messaging: inbound SAP status **message sinks** (`Order*StatusMessageSink`) + the `OrderStatusMessageManager` dispatcher. |
| **cmp** | Consignment Management Program — UI/web layer (droplets, form handlers); domain logic lives in base `com.klx.cmp.*`. |
| **kitting** | Kit orders, kit BOM, kit-complete, bailment stock; SAP kit service clients (`KitCompleteService`, `KitInfoService`). |
| **store** | Storefront web app (`beaerospace`/`be.war`), 534 JSPs incl. email templates; ATG B2B baseline + order form handlers + display droplets. |
| **ecomservice** | User/customer delta sync to SAP; Ecom REST API; Ping/DSI experience. |
| **feed** | Catalog/merchandising feeds using **Spring Batch** (PARALMerchModule framework + PARALCatalogMerchModule concrete feeds + PARALCommonModule + FeedMer). |
| **b2bsearch** | Endeca B2B part search integration. |
| **B2BOrderGateway** | SOAP web-service order gateway (JAX-WS). |
| **jaxrsrest / rest** | Shared JAX-RS scaffolding and REST pipeline config. |
| **fusion** | "Contact Us" / Fusion site helper. |
| **glasir** | DB change management (Liquibase + Groovy install scripts). |
| **beabcc** | BCC (Business Control Center) content onboarding. |
| **datamigration** | Standalone CSV/repository import/export + feed validation. |
| **dbupdate** | DB script runner. |
| **node / env** | Config-only modules: per-environment (`env/dev,prd,stg1..stg8`) and per-node-role (`node/ecom,merch,preview,aux1..3`) overrides layered on top of code modules. |

---

## 5. Architecture

### 5.1 ATG Nucleus component model (the app's "DI container")
**What it is:** ATG (Nucleus) is a component container much like Spring. Everything — services, schedulers, tools, form handlers — is a **component**: a plain Java class (usually extending `GenericService`) whose configuration and dependencies come from a `.properties` file that sits next to it on a config path.

**How it works:** `$class` names the Java implementation. Every other line maps to a **setter** on that class. A value starting with `/` is a **reference to another component**, resolved by its Nucleus path (like an `@Autowired` bean, but wired by path in config instead of by annotation). At startup ATG instantiates the class, calls the setters (injecting dependencies), then calls the lifecycle hook `doStartService()`.

```properties
$class=com.bdsi.servicehub.consignment.scheduler.UploadedReplenishmentNotificationScheduler
scheduler=/atg/dynamo/service/Scheduler                      # inject the ATG Scheduler component
emailTools=/com/bdsi/servicehub/common/email/SHEmailTools    # inject another component by path
schedule=every 15 minutes                                    # a scalar property (calls setSchedule)
enabled=false
```
**Why it matters / interview mapping:** This is dependency injection and inversion of control — the same idea as Spring `@Component`/`@Autowired` + `application.yml`, just expressed in `.properties`. If asked "does ATG have DI?", the answer is yes, and this is how.

### 5.2 Data layer — GSA repositories
**What it is:** ATG's **Generic SQL Adapter (GSA)** is an ORM-like persistence layer (comparable to JPA/Hibernate). You describe "item types" in XML that map to database tables and columns; ATG then loads/saves those items, caches them, and lets you query them with **RQL** (Repository Query Language) or raw SQL.

**How it works:** An `item-descriptor` maps an item type to a primary table; each `property` maps to a column with a data type. `enumerated` properties map integer codes to readable values (so the DB stores a code, the app sees a name). ATG also provides item caching (here `cache-mode="distributedHybrid"` for cluster caching).

```xml
<item-descriptor name="consignmentTransaction" sub-type-property="type" ...>
  <table name="shc_consignment_tnx" type="primary" id-column-names="id">
    <property name="type" data-type="enumerated">
      <option code="21" value="WAERCONS"/>   <!-- transaction type codes -->
    </property>
    <property name="isEmailSent" data-type="boolean" column-name="is_email_sent" default="false"/>
    <property name="state" data-type="enumerated">
      <option code="CONFIRMED" value="CONFIRMED"/> ... 
    </property>
  </table>
</item-descriptor>
```
Also heavy use of raw **JDBC / RQL** for complex reporting queries. Schema changes are managed with **Liquibase** in the `glasir` module.

### 5.3 Presentation
- **Storefront:** JSP + ATG droplets (`GetShelfLifeDroplet`, `CheckForKitDroplet`, `CertificateOfOriginDisplayDroplet`) + form handlers (`ProfileFormHandler`, order/checkout handlers).
- **APIs:** JAX-RS REST resources (Jersey) exposed for the CMP web UI, mobile app, and integrations (Swagger-documented).

### 5.4 Integration architecture (the heart of the system)
**The big picture:** ATG is the customer-facing commerce/inventory system; SAP/S4HANA is the ERP that actually fulfills and invoices. They talk two ways: **asynchronously** over IBM MQ for the order lifecycle (so SAP slowness never blocks the customer), and **synchronously** over an IBM API Connect REST gateway for lookups that need an immediate answer.
```
                    IBM MQ (async, TLS + client cert)
   ATG  ── OrderFulfillmentMessageSource ─────────────►  SAP / S4HANA
        ◄── WMQSinkConnector → OrderStatusMessageManager ── (status events)
        │        └─► Order*StatusMessageSink (confirmed/blocked/shipped/invoiced/...)
        │
        └── BDSISapEndpoint (sync REST via IBM API Connect) ──►  SAP services
                 (kit info, cardex docs, ship-to, EHS, incoterm, GTS ...)
   Secrets (MQ JKS pwd, JWT key, APIM secret) ← Azure Key Vault (Managed Identity)
   Files ↔ SFTP / Azure Blob ; analytics ↔ AWS S3 (Parquet)
```
**Why async for orders and sync for lookups:** Order creation is "fire and get callbacks later" — perfect for a queue that buffers and retries. A lookup like "what parts are in this kit?" needs an answer now — so it's a synchronous REST call. Choosing the right style per use case is a senior design point. (Full details in Doc 3.)

### 5.5 Batch & scheduled processing
There are two distinct batch families — worth calling out because they use different technologies:
- **ATG schedulers** — business jobs (notifications, report generation, file processing, cleanup) built on `SingletonSchedulableService`. "Singleton" means that even in a multi-node cluster, a distributed lock (`ClientLockManager`) ensures only one node runs the job — otherwise every node would fire it and send duplicate emails/orders. Dozens exist (see §7).
- **Spring Batch** — the `feed` module processes very large catalog/inventory/price feeds using chunk-oriented steps (reader → processor → writer), tasklets, deciders, and **partitioned parallelism**, coordinated by `batchConfig.xml` with a `JtaTransactionManager`. This is genuine Spring experience, distinct from the ATG schedulers.

### 5.6 Configuration layering (environments & nodes) — how one codebase serves many environments
**The problem:** the same code must run in dev, eight staging environments (stg1–8), and prod, each with different queue managers, URLs, secrets, and toggles — without branching the code.
**How ATG solves it:** configuration is **layered**. The effective value of a property is resolved by merging, in precedence order: base module config → `env/<env>` overrides → `node/<role>/<env>` overrides. `env/` folders hold per-environment overrides; `node/` folders hold per-node-role overrides (an `ecom` node runs different modules/config than a `merch` or `aux` node). A server starts with a module list and ATG merges the `.properties` accordingly.
**Why it matters / interview mapping:** this is externalized configuration and environment promotion done at the platform level — the same goal as Spring profiles + a Config Server / Azure App Configuration. Behavior differs by config, never by code, which keeps releases clean and auditable.

---

## 6. Core capabilities (by area) — the full breadth

### 6.1 Order management (base + ful)
- Domain: `KLXOrder`, `KLXOrderManager`, `KLXCommerceItemManager`, `KLXHardgoodShippingGroup`, `SHOrderTools`, `ServiceHubOrderTools`.
- Outbound to SAP: `OrderFulfillmentMessageSource` (Order XML), `ScrapMessageSource`.
- Inbound from SAP (ful message sinks): `OrderConfirmedStatusMessageSink`, `OrderBlockStatusMessageSink`, `OrderShippedStatusMessageSink`, `OrderInvoiceMessageSink`, `OrderChangeMessageSink`, `OrderDeleteMessageSink`, `OrderSOCompletionMessageSink`, `POOrderStatusMessageSink`, `POChangeOrderStatusMessageSink`, `DropShipPOMessageSink`, `OpenOrdersMessageSink`, `CustomerContractMessageSink`.
- Dispatcher: `OrderStatusMessageManager` (poll staged messages, route by type, retry, escalate).

### 6.2 Consignment inventory & transactions (servicehub + base cmp)
- `ConsignmentTransactionTools` / `ConsignmentTransactionHelper` — create/update transactions (issue, return, invoice, fillup).
- On-hand inventory: `SapConsignmentInventoryUtility`, on-hand cache adapters, `CMPInventoryManager` (base).
- Transaction types (repository enum): STR, PCN, ASN, IMM receipts/issue/return/stock-transfer, IOH, SHO, QRN, GRN, ISS, FSR/FSC, HAECO issue, WAERCONS, and the replenishment order type.

### 6.3 Bin management & scanning (servicehub)
- `BinTransactionManager` / `BinTransactionService`, bin map CRUD, bin labels (barcode), fedex scan, bin overstock.
- Scan processing: `ScanTransactionProcessor`, `BinScanProcessor`, validators; bin scan notification emails.

### 6.4 Replenishment (servicehub) — includes the feature I built
- `FSReplenishmentFileProcessor` (Flight Star), `WaerlinxFileProcessor` (GKN consumption), fillup/issue order creation processors.
- **SH-7690 (my feature):** `UploadedReplenishmentNotificationScheduler` + `UploadedReplenishmentExcelReport` + `SHEmailTools.sendUploadedReplenishmentReportEmail` — after a batch of replenishment orders is uploaded for a customer, generate an Excel report (with SAP confirmation data) and email the submitter; scheduler-based with a delay window and `is_email_sent` idempotency.

### 6.5 Kitting (kitting module)
- SAP clients: `KitCompleteService` (kit-complete/bailment stock via MQ + REST), `KitInfoService`, `KitCreationService` (ATG→MDG), `KitBomService`, `DeliveryReportService`.
- Kit setup, work orders, approvals, crib, Agusta-specific handling, SFTP file service.

### 6.6 Feeds (servicehub + feed + base cardex)
- **Inbound customer/consumption feeds:** BAE, Cessna, Bell, Boeing 4PL, BDS, Waerlinx/GKN.
- **EHS feeds:** `SapEhsCasFeedFileProcessor`, `SapEhsCmpntsReglnsFeedFileProcessor`, `SapEhsPartFeedFileProcessor` + `SapEhsFeedScheduler` (hazardous-substance/regulatory data).
- **Catalog/merch feeds:** Spring Batch (feed module) — product, inventory, price.
- **Cardex feeds (base):** product/sales-contract file processors (legacy inventory system).
- **File Mapping Network framework:** config-driven inbound/outbound file layouts per customer (avoids per-customer code).

### 6.7 Reporting (servicehub)
Excel/PDF reports: `UploadedReplenishmentExcelReport`, `HaecoFailedIssTransactionExcelReport`, `GKNConsumptionTransactionExcelReport`, `DuplicateTransactionExcelReport`, `OnHandInventoryCorrectionExcelReport`, `ConsignmentConsumptionReport`, plus report schedulers (blocked/failed orders, consolidated consumption, bin-map mismatch, ASN). PDF via FOP/PDFBox; barcodes via barcode4j.

### 6.8 Audit & reconciliation
- Audit trail (`AuditTrailTools`, audit repository + REST).
- `OrderReconciliationService` — reconcile open-order discrepancies between S4 and ServiceHub.
- Every inbound/outbound SAP message logged (`order_txn_xml`) for traceability (SH-1894).

### 6.9 Security
- **JWT** (`JwtHelper`, HS256, key from Key Vault) for storefront auth cookies, password-reset links; **auth0 java-jwt** in `SecurityTools` for API-client tokens.
- **Ping Identity SSO** (`com.ecom.pingone`, `PingTools`), **Twilio MFA**.
- JAX-RS security filters: CSRF protection, logged-in access checker, exception mappers.

### 6.10 Customer & profile management
- ATG profiles/organizations; customer programs; user/customer delta sync to SAP (`ecomservice`); org-code and distribution-list management.

---

## 7. Schedulers inventory (breadth of batch processing)
**Consignment/notification:** `UploadedReplenishmentNotificationScheduler`, `GKNConsumptionNotificationScheduler`, `HaecoFailedIssueTransactionNotificationScheduler`, `DuplicateTransactionNotificationScheduler`, `BlockedAndFailedOrderReportScheduler`, `ConsolidatedPartConsumptionReportScheduler`, `ImmASNReportScheduler`, `PCNEmailReportScheduler`, `FlightStarReplenishmentReportScheduler`, `TricorderRetirementNotificationScheduler`.
**Order creation/processing:** `CreateFillupOrderRequestScheduler`, `CreateIssueOrderRequestScheduler`, `CreateGEAsnIssueOrderScheduler`, `AutoReceiptSTRFileScheduler`, `ASNOutboundFileProcessScheduler`.
**Feed/file:** `SapEhsFeedScheduler`, `CMIRFileImportScheduler`, `AslCodeFileImportScheduler`, `FedexBinMapFeedScheduler`, `FileMappingFeedScheduler`, `InboundFileProcessScheduler`, `OutboundFileProcessScheduler`, `MFTSFileMappingScheduler`, `GenericMaintenanceScheduler`, `FileMappingNetworkLogsPurgeScheduler`, `BinMapMisMatchReportScheduler`.
**Cleanup:** `TruncateStagedReportsScheduler`, `PurgeOrderStatusMessageManager`.
**Messaging dispatcher:** `OrderStatusMessageManager` (SchedulableService).

---

## 8. End-to-end flow example (replenishment → SAP → notification)
```
1. Customer uploads a replenishment file (or a bin scan triggers a fillup).
2. FSReplenishmentFileProcessor parses & validates rows → creates consignmentTransaction records.
3. Fillup order created in ATG → OrderFulfillmentMessageSource sends Order XML to SAP over IBM MQ.
4. SAP creates the sales order and asynchronously returns CONFIRMED / BLOCKED / SHIPPED / INVOICED events.
5. WMQSinkConnector reads events → OrderStatusMessageManager stages & dispatches →
   OrderConfirmedStatusMessageSink (etc.) update the ATG order with SAP order#, ship dates, state.
6. After a delay window, UploadedReplenishmentNotificationScheduler picks up the batch
   (is_email_sent=0, >15 min old), builds an Excel report (POI) including SAP confirmation data,
   emails the submitter via SHEmailTools, then marks is_email_sent=1 transactionally.
Secrets used along the way (MQ JKS password, JWT key, SAP APIM secret) come from Azure Key Vault.
```

---

## 9. My role & contributions
- Backend development in the **ServiceHub** module: schedulers, file processors, Excel reporting, and email notifications.
- **SH-7690** (uploaded replenishment notification) — designed and built the scheduler, report, VO, email method, and template; modeled it on existing notification patterns (GKN/HAECO/ImmASN) for consistency.
- Debugging/fixes: Excel date formatting (real date values + locale-proof format), column-width tuning, safe dead-code removal, Git cherry-pick to UAT.
- Cross-cutting understanding of the SAP/MQ integration, Azure Key Vault security, and the config layering.

---

## 10. Interview Q&A (project-level)

**Q: Describe the architecture in 2 minutes.**
ATG monolith (component + repository model) fronting an aerospace consignment business, integrated with SAP asynchronously over IBM MQ (order create + status events via message sinks) and synchronously over an IBM API Connect REST gateway. Backend logic in the ServiceHub module; storefront in the store module; catalog feeds via Spring Batch; secrets in Azure Key Vault; analytics on AWS S3/Parquet. Config is layered per environment and node.

**Q: How is it deployed/configured across environments?**
Config-only `env/` and `node/` modules layer overrides on top of code modules; a node starts with a module list and ATG merges `.properties` by precedence — so stg1–8 and prod differ by config, not code.

**Q: How does ATG compare to Spring?**
Nucleus components ≈ Spring beans (DI via `.properties` vs annotations); GSA repositories ≈ JPA; droplets/form handlers ≈ MVC controllers; `SingletonSchedulableService` ≈ `@Scheduled` + a distributed lock. I can map any ATG pattern to its Spring Boot equivalent.

**Q: What are the biggest technical risks?**
Tight SAP coupling (mitigated by async MQ + retry), a large monolith (being modernized to Azure/services), and committed secrets in some legacy config (should move fully to Key Vault).

---

## 11. Glossary
- **Consignment / VMI** — Boeing-owned stock held at the customer; billed on consumption.
- **Fillup / replenishment order** — refills consignment bins.
- **Bin scan** — scanning a bin (empty/full) to trigger replenishment / record usage.
- **SoldTo / ShipTo** — billing (parent) customer vs receiving location.
- **Bailment** — a consignment arrangement; "BAIL" flag on parts.
- **GSA** — Generic SQL Adapter, ATG's repository/ORM layer.
- **Nucleus** — ATG's component container.
- **Droplet** — an ATG server-side rendering component used in JSPs.
- **RQL** — Repository Query Language (ATG).
- **APIM** — IBM API Connect gateway fronting SAP.
- **EHS** — Environmental/Hazardous Substance (regulatory) data.
