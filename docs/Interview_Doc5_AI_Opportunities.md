# Doc 5 — AI Opportunities in BDSI (Senior Technical Lead view)

How I, as a senior technical lead, would introduce AI/ML into this B2B commerce + consignment-inventory + SAP-integration platform. Each idea maps to a real capability in the system, framed for AI-role interviews: **problem → AI approach → data → architecture → value → guardrails.**

> **Opening:** "The highest-value AI in BDSI isn't a chatbot — it's forecasting replenishment, auto-triaging SAP order exceptions, document/file intelligence for our many customer feeds, and search relevance. All use data we already generate: consumption transactions, order-status events, bin scans, and free-text exceptions. I'd build them Azure-native with human-in-the-loop and measurable KPIs."

---

## 1. Predictive replenishment / demand forecasting ⭐ (top idea)
- **Problem:** Fillup/replenishment orders are reactive (threshold/bin-scan based) → stock-outs and over-stock across many customers/parts.
- **AI:** Time-series demand forecasting per customer + part → recommend proactive qty & timing. Baseline Prophet/ARIMA; scale with XGBoost/LightGBM or Temporal Fusion Transformer.
- **Data (already in system):** `shc_consignment_tnx` consumption history, Waerlinx/GKN/BAE/Cessna consumption feeds, on-hand inventory, bin scans, order/lead times, seasonality.
- **Architecture:** Azure ML training pipeline → model registry → scoring microservice → feeds the existing `CreateFillupOrderRequestScheduler` with recommended quantities instead of a fixed threshold.
- **Value:** fewer stock-outs, less capital in over-stock, higher service levels.
- **Guardrails:** cold-start fallback to rules, human approval for large orders, drift monitoring.

## 2. Order exception triage & classification ⭐
- **Problem:** SAP returns CONFIRMED/BLOCKED/FAILED with free-text `remarks` (contract issues, credit blocks, SAP errors); analysts read/triage manually (also the failed-status/report queues in `OrderStatusMessageManager`).
- **AI:** Text classification of exception messages → category + recommended action; LLM to summarize a batch ("18/20 are contract mismatches for customer X").
- **Data:** historical `remarks` + resolution outcomes (labels); `order_txn_xml` audit.
- **Architecture:** classifier or Azure OpenAI (structured prompt/function-calling) invoked when the notification scheduler builds a report → enrich Excel/email with categorized, prioritized exceptions and auto-route to the right team.
- **Guardrails:** confidence thresholds, "unclassified" bucket, recommend-not-resolve, keep raw text.

## 3. Document & file intelligence (IDP) ⭐
- **Problem:** Many customer-specific inbound file formats; the File Mapping framework needs manual config per customer, and malformed files cause failures.
- **AI:** Azure AI Document Intelligence / LLM to auto-detect columns, map headers to the canonical schema, validate/repair rows, and suggest a File Mapping config for a new customer.
- **Data:** existing file samples + known-good mappings (few-shot examples).
- **Architecture:** pre-processing service normalizes any inbound file → canonical schema with a confidence score; low-confidence → human review UI before ingest.
- **Value:** faster customer onboarding, fewer file failures, less manual mapping.

## 4. Natural-language reporting / "chat with your orders"
- **Problem:** PMs export Excel reports and slice them manually.
- **AI:** RAG + **text-to-SQL** over consignment/order data: "failed fillup orders for TEXTRON last month with contract issues" → safe parameterized query → table/summary.
- **Architecture:** Azure OpenAI + text-to-SQL over **read-only** allow-listed views; RAG over docs/contracts.
- **Guardrails:** read-only, query allow-listing, row-level security by customer, validate generated SQL, cost caps.

## 5. Anomaly detection on transactions & feeds
- **Problem:** Duplicate transactions, quantity spikes, bad feed data (today caught by rules like `DuplicateTransactionNotificationScheduler`).
- **AI:** Unsupervised anomaly detection (Isolation Forest / autoencoders) on transaction and feed-volume streams to flag unusual qty/part/customer combos before they create bad orders.
- **Guardrails:** alert-only first, tune false positives, combine with existing rules.

## 6. Search relevance & catalog AI (Endeca → semantic)
- **Problem:** Aerospace part search (Endeca) is keyword/facet based; users struggle with part synonyms/cross-references.
- **AI:** Semantic search / vector embeddings + LLM query understanding for part lookup, "find equivalent/superseded part," and natural-language catalog queries.
- **Value:** faster part discovery, fewer wrong orders.

## 7. Support & operations copilot (internal)
- **Problem:** L2 support investigates stuck orders across ATG + SAP + MQ logs (`order_txn_xml`).
- **AI:** RAG-grounded LLM copilot over runbooks + order/txn data + message audit: "why is order o12345678 stuck?" → explanation + suggested fix.
- **Guardrails:** trusted sources only, cite sources, no writes without confirmation, redact PII/secrets.

## 8. Kitting & BOM intelligence
- **Problem:** Kitting (kit BOM, kit-complete, bailment) involves manual setup and validation.
- **AI:** Recommend kit compositions, predict kit demand, and validate BOMs against contracts/consumption patterns.

## 9. AI-assisted engineering & modernization (meta, lead-level)
- **Code understanding & docs:** LLMs to summarize modules and generate onboarding docs (like these interview docs).
- **Test generation:** draft unit tests before refactoring to raise coverage.
- **Migration assist:** LLM-assisted ATG→Spring Boot skeletons (scheduler→`@Scheduled`+ShedLock, repository→JPA) with human review.
- **PR review copilot:** flag security/secret/breaking-API risks.
- **Guardrails:** AI drafts, humans approve, never auto-merge, no secrets in prompts.

---

## Reference architecture (Azure-native, fits this shop)
```
Data (Oracle txns, consumption/bin-scan feeds, MQ audit, files)
  → Ingest/feature pipeline (Azure Data Factory / Databricks)
  → Train + registry (Azure Machine Learning)   |   LLM (Azure OpenAI) + RAG
  → Model/scoring microservice (Spring Boot on AKS / App Service / Functions)
  → Consumed by existing schedulers/services (fillup creation, notification, file processors, search)
  → Observability (App Insights); feedback loop (human decisions → labels)
Secrets via Azure Key Vault (Managed Identity) — the pattern already in use.
```

## MLOps & Responsible AI (explained — matters in an aerospace/defense context)

**MLOps (running models like software):**
- **Data & feature versioning** — reproduce any model by pinning the exact training data/features; without this you can't debug why a model changed.
- **Model registry** — a versioned store of trained models with metadata; you promote a specific version to prod and can roll back.
- **CI/CD for models** — automated retrain → validate → deploy pipelines, so model updates are as controlled as code releases.
- **Canary / shadow deploys** — run the new model on a small slice of traffic (canary) or alongside the old one without acting on its output (shadow) to compare before full rollout.
- **Monitoring** — track **data drift** (inputs change), **concept drift** (the relationship changes), and prediction quality; alert and retrain when they degrade.

**Human-in-the-loop:** ship models as **recommendations** first (e.g. "suggested replenishment qty") with a person approving; only automate once accuracy and trust are proven. This limits blast radius if the model is wrong.

**Responsible AI guardrails:**
- **Privacy/security** — never put PII or secrets in LLM prompts; ground RAG only on access-controlled, trusted sources; log and audit AI decisions.
- **Explainability** — surface *why* a forecast/classification was made and keep the raw data, so users and auditors can trust it.
- **Fallbacks** — if the model is unavailable or low-confidence, fall back to the existing rules (never a hard dependency).
- **Bias/fairness** — validate the model doesn't systematically disadvantage certain customers/parts.
- **Export control & data residency** — aerospace/defense data is regulated (ITAR/EAR-style); keep data in approved regions and don't send controlled data to external model endpoints. This is a critical, differentiating point to raise.
- **Cost control** — batch requests, cache results, cap token usage; LLM calls cost money and add latency.

**Build vs buy:** prefer managed services (Azure OpenAI, Azure AI Document Intelligence, Azure ML) over building models/infra from scratch — faster to deliver, less to operate, and they inherit the platform's security/compliance posture.

## AI-interview Q&A (use project as evidence)
- **Where does AI add most value?** Demand forecasting + exception triage — high-volume, currently manual/reactive, data already exists.
- **RAG vs fine-tuning?** Start RAG (cheaper, updatable, grounded); fine-tune only for consistent domain style with enough labeled data.
- **Prevent hallucinations?** RAG over trusted sources, text-to-SQL with read-only allow-listed views, citations, confidence thresholds, human-in-the-loop.
- **Productionize a model?** Azure ML pipeline + registry → scoring microservice → integrate via API into existing schedulers → monitor drift/quality → retrain → canary.
- **Biggest risks?** Wrong forecasts (stock-out/over-stock), mis-classification, privacy/export-control, cost — mitigate with HITL, fallbacks, guardrails, monitoring.
- **Success metrics?** Business KPIs — stock-out rate, over-stock cost, exception resolution time, file-onboarding time, support MTTR — not just model accuracy.

## 60-second pitch (memorize)
"As a lead on a Boeing consignment-inventory platform, I'd prioritize two AI wins: demand forecasting to make replenishment proactive instead of threshold-based, and ML classification of SAP order exceptions to auto-triage the free-text errors analysts read today. Both use data we already have — consumption transactions and order-status history. I'd build them Azure-native: Azure ML or Azure OpenAI behind a scoring microservice that plugs into our existing schedulers, with Key Vault for secrets, human-in-the-loop approval, drift monitoring, and rule-based fallbacks. The principle is measurable business value with responsible-AI guardrails — which matter especially in an aerospace context with export-control and data-residency requirements."
