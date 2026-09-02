# AI Contract Analyzer — Microservices Project Writeup

---

## 1. Elevator Pitch (30 seconds)

> "I built a production-grade, containerized **AI Contract Analyzer** — a microservices application where users upload PDF contracts and ask natural-language questions about them. It uses a **RAG (Retrieval-Augmented Generation)** pipeline with a local LLM, so answers come strictly from the uploaded document, not the model's imagination. The system is fully reactive (Spring WebFlux), uses **PostgreSQL with pgvector** for semantic search, **Redis** for caching and rate limiting, **Kafka** for async audit events, a separate **audit microservice** with circuit-breaker resilience, distributed tracing via **Zipkin**, and is deployed to Google Cloud using **Terraform** infrastructure-as-code with secrets managed by **GCP Secret Manager**."

---

## 2. What Problem Does It Solve?

Legal and business teams deal with lengthy contracts. Finding a specific clause ("What are the payment terms?", "When can we terminate?") means manually reading pages. This app lets you upload a contract and simply ask — the AI answers using only the actual contract text, with guardrails against hallucination.

---

## 3. Architecture Overview

```
                         ┌──────────────────┐
                         │  React Frontend  │  (Port 3000)
                         │  Vite + SSE      │
                         └────────┬─────────┘
                                  │ REST / SSE
                         ┌────────▼─────────────────────────────┐
                         │      Backend (Spring WebFlux)         │  (Port 8000)
                         │  - Upload & RAG orchestration         │
                         │  - ChatClient (Spring AI)             │
                         └──┬────────┬────────┬────────┬─────────┘
                            │        │        │        │
              ┌─────────────▼──┐ ┌───▼────┐ ┌─▼─────┐ ┌▼──────────────┐
              │  PostgreSQL    │ │ Redis  │ │ Ollama│ │  Kafka         │
              │  + pgvector    │ │ Cache  │ │ LLM + │ │  (async audit) │
              │  (vectors)     │ │ +Rate  │ │ Embed │ │                │
              └────────────────┘ └────────┘ └───────┘ └───┬────────────┘
                                                          │ consume
              ┌───────────────────────────┐    HTTP   ┌───▼────────────────┐
              │  Audit Microservice        │◀──────────│  (also HTTP for    │
              │  Spring Boot (Port 8082)   │  Circuit  │   upload audits)   │
              │  Logs all contract events  │  Breaker  └────────────────────┘
              └───────────────────────────┘
                                  │
                         ┌────────▼─────────┐
                         │  Zipkin          │  (Port 9411)
                         │  Distributed     │
                         │  Tracing         │
                         └──────────────────┘
```

---

## 4. Technology Stack & Why Each Was Chosen

| Layer | Technology | Why |
|-------|-----------|-----|
| Frontend | React 18 + Vite | Fast, modern, native fetch/streaming — no heavy UI framework |
| Backend | Java 21 + Spring Boot 3.3 **WebFlux** | Reactive/non-blocking — critical for streaming LLM tokens to many users |
| AI Orchestration | Spring AI (ChatClient + VectorStore) | Clean abstraction over LLMs and vector DBs |
| LLM | Ollama + Llama 3.2 | 100% local, free, no external API costs |
| Embeddings | nomic-embed-text (768-dim) | Local embedding model for semantic search |
| Vector DB | PostgreSQL 16 + pgvector | Stores embeddings, HNSW index for fast similarity search |
| Cache | Redis 7 | Response caching, rate limiting, chat history |
| Messaging | Apache Kafka | Async, decoupled audit event streaming |
| Resilience | Resilience4j | Circuit breaker for the audit service HTTP calls |
| Tracing | Zipkin | Distributed tracing across services |
| Secrets | GCP Secret Manager | Secure credential storage (no secrets in code) |
| IaC | Terraform | Reproducible GCP infrastructure |
| Containers | Docker Compose | Orchestrates all 8 services |

---

## 5. The Two Core Flows

### Flow A: Document Upload & Indexing
1. User uploads PDF → backend
2. **Apache Tika** extracts raw text
3. **TokenTextSplitter** chunks it (1000 tokens, 200 overlap)
4. Each chunk tagged with `contract_id` + `user_id` (multi-tenant isolation)
5. **Ollama** converts each chunk to a 768-dimension vector (embedding)
6. Vectors stored in **PostgreSQL pgvector** with an HNSW index
7. Redis cache invalidated (old answers now stale)
8. Audit event fired to the **audit microservice** (synchronous HTTP with circuit breaker)

### Flow B: Question Answering (RAG)
1. User asks a question → backend
2. **Rate limit check** (Redis — 20/min per user)
3. **Cache check** (Redis — instant answer if seen before)
4. On cache miss: question embedded → **vector similarity search** (top 3 chunks, filtered by contract/user)
5. Retrieved chunks form the **context**
6. **Prompt built** with strict guardrails + context + question
7. **Llama 3.2 streams** the answer token by token
8. Streamed to browser via **Server-Sent Events (SSE)**
9. After completion: response cached, chat history saved, **Kafka audit event** published (async)

---

## 6. Microservices Design Highlights (Talk About These)

### a) Reactive, Non-Blocking Backend
Built on Spring WebFlux. Uses `Mono`/`Flux` end-to-end. Blocking operations (PDF parsing, DB writes) are offloaded to `Schedulers.boundedElastic()` so the event loop never blocks. This lets a handful of threads serve thousands of concurrent streaming connections.

### b) Two Communication Patterns for Audit
- **Synchronous (HTTP + WebClient)** for upload events — needs a confirmation the upload was audited.
- **Asynchronous (Kafka)** for chat events — fire-and-forget, high throughput, decoupled.

This demonstrates understanding of *when* to use sync vs async messaging.

### c) Circuit Breaker (Resilience4j)
The audit HTTP call is wrapped in a circuit breaker:
- **CLOSED** → normal
- **OPEN** → after >50% failures, stop calling for 30s (prevents cascading failure)
- **HALF_OPEN** → test recovery with a few calls
If the audit service is down, the main flow still works via a fallback — the app degrades gracefully.

### d) Multi-Tenant Data Isolation
Every vector search filters by `contract_id AND user_id` inside the SearchRequest expression. Even with one shared table, users only ever see their own contract's data.

### e) Caching Strategy (Cache-Aside)
Redis stores LLM responses keyed by a hash of (contract + user + question). Repeat questions return in ~2ms instead of 5-10 seconds. TTL of 1 hour + active invalidation on new upload keeps answers fresh.

### f) Observability
Zipkin provides distributed tracing — you can follow a single request as it flows through backend → audit service → Kafka, seeing the latency of each hop.

---

## 7. DevOps & Deployment Story

- **Docker Compose** orchestrates 8 containers (frontend, backend, audit, db, redis, ollama, kafka, zookeeper, zipkin) on a shared bridge network with health checks and dependency ordering.
- **Terraform** provisions the entire GCP infrastructure (VM, static IP, firewall rules) from code — `terraform apply` spins up everything, `terraform destroy` tears it down.
- **GCP Secret Manager** stores all passwords; the backend fetches them at startup via the `gcp` Spring profile (`${sm://secret-name}`), so no credentials live in the repo.
- **Kubernetes manifests** also exist (Deployments, StatefulSet for DB, HPA autoscaling, Ingress) demonstrating container orchestration knowledge.

---

## 8. Real Problems I Solved (Great Interview Stories)

| Problem | Root Cause | Fix |
|---------|-----------|-----|
| App worked on my machine but not a colleague's | GCP firewall blocked ports; corporate network blocks non-standard ports | Added GCP ingress firewall rule + mapped frontend to port 80 |
| GCP Secret Manager profile not activating | `SPRING_PROFILES_ACTIVE` shell var didn't reach the container | Added `${SPRING_PROFILES_ACTIVE:-default}` to docker-compose |
| IAM binding command failed on Windows | PowerShell doesn't support bash `$()` subshell | Split into two explicit commands |
| Audit service failures could crash main flow | No resilience | Added Resilience4j circuit breaker with fallback |

---

## 9. What I'd Improve Next (Shows Maturity)

- **Authentication/Authorization**: Spring Security + JWT; derive `user_id`/`contract_id` from token instead of hardcoding.
- **CI/CD**: GitHub Actions pipeline (build → test → push image → deploy).
- **Testing**: Unit tests (JUnit + Mockito) and integration tests (Testcontainers for Postgres/Redis/Kafka).
- **Kafka consumer** in the audit service to fully close the async loop.
- **Horizontal scaling** of the backend behind a load balancer (stateless design already supports this since state is in Redis/Postgres).

---

## 10. Key Talking Points Summary

1. **RAG pipeline** — retrieval + generation, prevents hallucination
2. **Reactive streaming** — WebFlux + SSE for real-time token delivery
3. **Vector search** — pgvector + HNSW + 768-dim embeddings + cosine similarity
4. **Polyglot communication** — REST (sync) + Kafka (async)
5. **Resilience** — circuit breaker, graceful degradation
6. **Caching & rate limiting** — Redis, cache-aside pattern
7. **Observability** — Zipkin distributed tracing
8. **Cloud-native** — Docker, Kubernetes, Terraform IaC, GCP Secret Manager
9. **Multi-tenancy** — metadata-based row isolation
10. **Security** — externalized secrets, no credentials in code

---

*This project demonstrates full-stack + AI + microservices + DevOps competency end to end.*
